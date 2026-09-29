package com.payflow.railsim;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stand-in for the external settlement rails (card network, UPI switch, bank transfer). A separate process that
 * PayFlow reaches over HTTP, so timeouts, connection failures, slowness and "accepted but the answer never arrived"
 * are real network behaviour and not in-process simulations.
 *
 * <p>It behaves like a real payment service provider in the ways that matter to PayFlow's correctness:
 * <ul>
 *   <li><b>Idempotency by key:</b> the first submission with a key decides the outcome; later submissions with the
 *       same key return that outcome and never move money twice.</li>
 *   <li><b>Status inquiry:</b> {@code GET /rails/{rail}/transfers/{key}} returns the recorded outcome or 404
 *       (never received). This is how an operator resolves an unknown outcome.</li>
 *   <li><b>Void:</b> {@code POST /rails/{rail}/transfers/{key}/void} blocks the key: a submission that arrives later
 *       (for example one stuck in a network queue) is declined {@code VOIDED}. A key that was already accepted
 *       cannot be voided (409).</li>
 *   <li><b>Capacity:</b> at most {@code capacity} concurrent submissions; beyond that it answers 429, as providers do.</li>
 * </ul>
 *
 * <p>Fault injection for failure tests, at runtime via {@code POST /admin/faults} (JSON: latencyMs, jitterMs,
 * errorRate, errorMode = UNAVAILABLE_503 | TIMEOUT_AFTER_ACCEPT | RESET, capacity), or per request through the
 * payment reference: {@code SIM-DECLINE}, {@code SIM-UNAVAILABLE} (503 before processing) and {@code SIM-TIMEOUT}
 * (accepted, then answers after 30 s, so the client times out with the money already moved).
 *
 * <p>This is a test double: the admin endpoints are unauthenticated and state is in memory. It is never deployed
 * outside the lab and test environments.
 */
public final class RailSimulator {

    private static final Pattern TRANSFER = Pattern.compile("^/rails/([A-Z_]+)/transfers$");
    private static final Pattern TRANSFER_BY_KEY = Pattern.compile("^/rails/([A-Z_]+)/transfers/([^/]+)$");
    private static final Pattern VOID = Pattern.compile("^/rails/([A-Z_]+)/transfers/([^/]+)/void$");
    private static final BigDecimal UPI_LIMIT_INR = new BigDecimal("100000");

    private record Outcome(String status, String providerReference, String declineReason) {
        String json() {
            return "{\"status\":\"" + status + "\",\"providerReference\":" + str(providerReference)
                    + ",\"declineReason\":" + str(declineReason) + "}";
        }
    }

    /** Current fault configuration; replaced atomically. */
    public record Faults(long latencyMs, long jitterMs, double errorRate, String errorMode, int capacity) {
        public static final Faults NONE = new Faults(0, 0, 0, "UNAVAILABLE_503", 64);
    }

    private final Map<String, Outcome> outcomes = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final AtomicLong submissions = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong declined = new AtomicLong();
    private final AtomicLong throttled = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong inquiries = new AtomicLong();
    private volatile Faults faults = Faults.NONE;
    private HttpServer server;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("RAIL_SIM_PORT", "8090"));
        RailSimulator sim = new RailSimulator().start(port);
        System.out.println("rail simulator listening on " + sim.port());
    }

    public RailSimulator start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 512);
        // One virtual thread per exchange: slow-provider simulation must not be limited by a small pool.
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
        return this;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    public void setFaults(Faults f) {
        this.faults = f;
    }

    public void reset() {
        faults = Faults.NONE;
        outcomes.clear();
        maxInFlight.set(0);
    }

    /** Recorded outcome for a key, or null when the rail never received it. Tests use this as ground truth. */
    public String recordedStatus(String rail, String key) {
        Outcome o = outcomes.get(rail + "/" + key);
        return o == null ? null : o.status();
    }

    /**
     * Test hook: the rail processed an instruction whose answer never reached PayFlow (e.g. the response was lost).
     * Lets tests create the "money moved but we do not know it" state deterministically.
     */
    public void recordAccepted(String rail, String key) {
        outcomes.putIfAbsent(rail + "/" + key, new Outcome("ACCEPTED", "SIM-" + UUID.randomUUID(), null));
    }

    public int maxObservedInFlight() {
        return maxInFlight.get();
    }

    public long submissions() {
        return submissions.get();
    }

    private void handle(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();
            Matcher m;
            if ("POST".equals(method) && (m = TRANSFER.matcher(path)).matches()) {
                submit(ex, m.group(1), body(ex));
            } else if ("POST".equals(method) && (m = VOID.matcher(path)).matches()) {
                voidTransfer(ex, m.group(1), m.group(2));
            } else if ("GET".equals(method) && (m = TRANSFER_BY_KEY.matcher(path)).matches()) {
                inquiries.incrementAndGet();
                Outcome o = outcomes.get(m.group(1) + "/" + m.group(2));
                send(ex, o == null ? 404 : 200, o == null ? "{\"status\":\"NOT_FOUND\"}" : o.json());
            } else if ("POST".equals(method) && "/admin/faults".equals(path)) {
                Map<String, String> f = parse(body(ex));
                faults = new Faults(num(f, "latencyMs", 0), num(f, "jitterMs", 0),
                        Double.parseDouble(f.getOrDefault("errorRate", "0")), f.getOrDefault("errorMode", "UNAVAILABLE_503"),
                        (int) num(f, "capacity", 64));
                send(ex, 200, stats());
            } else if ("DELETE".equals(method) && "/admin/faults".equals(path)) {
                faults = Faults.NONE;
                send(ex, 200, stats());
            } else if ("GET".equals(method) && "/admin/stats".equals(path)) {
                send(ex, 200, stats());
            } else if ("GET".equals(method) && "/health".equals(path)) {
                send(ex, 200, "{\"status\":\"UP\"}");
            } else {
                send(ex, 404, "{\"error\":\"not found\"}");
            }
        } catch (RuntimeException e) {
            // Malformed input from the client is a contract violation: 400, not a provider outage.
            try {
                send(ex, 400, "{\"error\":\"INVALID_INSTRUCTION\"}");
            } catch (IOException | RuntimeException ignored) {
                // response already started or connection gone
            }
        } catch (IOException e) {
            // client went away (e.g. it timed out first): nothing to answer
        } finally {
            ex.close();
        }
    }

    private void submit(HttpExchange ex, String rail, String rawBody) throws IOException {
        submissions.incrementAndGet();
        Faults f = faults;
        int now = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(now, Math::max);
        try {
            if (now > f.capacity()) {
                throttled.incrementAndGet();
                ex.getResponseHeaders().add("Retry-After", "1");
                send(ex, 429, "{\"error\":\"RATE_LIMITED\"}");
                return;
            }
            Map<String, String> req = parse(rawBody);
            String key = req.get("idempotencyKey");
            String reference = req.getOrDefault("reference", "");
            if (key == null || key.isBlank() || req.get("amount") == null || req.get("currency") == null) {
                send(ex, 400, "{\"error\":\"INVALID_INSTRUCTION\"}");
                return;
            }
            Outcome known = outcomes.get(rail + "/" + key);
            if (known != null) {
                // A key the rail already holds is answered from its record: that is what idempotency means.
                send(ex, 200, known.json());
                return;
            }
            sleep(f.latencyMs() + (f.jitterMs() > 0 ? ThreadLocalRandom.current().nextLong(f.jitterMs() + 1) : 0));
            boolean injected = f.errorRate() > 0 && ThreadLocalRandom.current().nextDouble() < f.errorRate();
            if (reference.contains("SIM-UNAVAILABLE") || (injected && "UNAVAILABLE_503".equals(f.errorMode()))) {
                failed.incrementAndGet();
                send(ex, 503, "{\"error\":\"UNAVAILABLE\"}");
                return;
            }
            if (injected && "RESET".equals(f.errorMode())) {
                failed.incrementAndGet();
                ex.close(); // drop the connection without a response: the client sees an I/O error
                return;
            }
            Outcome outcome = outcomes.computeIfAbsent(rail + "/" + key, k -> decide(rail, req, reference));
            if (reference.contains("SIM-TIMEOUT") || (injected && "TIMEOUT_AFTER_ACCEPT".equals(f.errorMode()))) {
                sleep(30_000); // processed, but the answer arrives far too late: the client's outcome is UNKNOWN
            }
            send(ex, 200, outcome.json());
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private Outcome decide(String rail, Map<String, String> req, String reference) {
        String decline = null;
        if (reference.contains("SIM-DECLINE")) {
            decline = "SIMULATED_DECLINE";
        } else if ("UPI".equals(rail) && !"INR".equals(req.get("currency"))) {
            decline = "UPI_CURRENCY_NOT_SUPPORTED";
        } else if ("UPI".equals(rail) && new BigDecimal(req.get("amount")).compareTo(UPI_LIMIT_INR) > 0) {
            decline = "UPI_LIMIT_EXCEEDED";
        }
        if (decline != null) {
            declined.incrementAndGet();
            return new Outcome("DECLINED", null, decline);
        }
        accepted.incrementAndGet();
        String prefix = switch (rail) {
            case "CARD_NETWORK" -> "CARD";
            case "UPI" -> "UPI";
            default -> "BANK";
        };
        return new Outcome("ACCEPTED", prefix + "-" + UUID.randomUUID(), null);
    }

    private void voidTransfer(HttpExchange ex, String rail, String key) throws IOException {
        Outcome o = outcomes.computeIfAbsent(rail + "/" + key, k -> new Outcome("DECLINED", null, "VOIDED"));
        if ("ACCEPTED".equals(o.status())) {
            send(ex, 409, o.json());
        } else {
            send(ex, 200, o.json());
        }
    }

    private String stats() {
        Faults f = faults;
        return "{\"submissions\":" + submissions.get() + ",\"accepted\":" + accepted.get() + ",\"declined\":" + declined.get()
                + ",\"throttled\":" + throttled.get() + ",\"failed\":" + failed.get() + ",\"inquiries\":" + inquiries.get()
                + ",\"inFlight\":" + inFlight.get() + ",\"maxInFlight\":" + maxInFlight.get()
                + ",\"faults\":{\"latencyMs\":" + f.latencyMs() + ",\"jitterMs\":" + f.jitterMs() + ",\"errorRate\":"
                + f.errorRate() + ",\"errorMode\":\"" + f.errorMode() + "\",\"capacity\":" + f.capacity() + "}}";
    }

    // ------------------------------------------------------------------------------------------------ plumbing

    private static String body(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readNBytes(64 * 1024), StandardCharsets.UTF_8);
        }
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final Pattern PAIR = Pattern.compile("\"([A-Za-z]+)\"\\s*:\\s*(\"((?:[^\"\\\\]|\\\\.)*)\"|[-0-9.eE]+|true|false|null)");

    /** Flat JSON object parser: the simulator's requests are flat string/number maps. */
    static Map<String, String> parse(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = PAIR.matcher(json == null ? "" : json);
        while (m.find()) {
            String value = m.group(3) != null ? m.group(3) : m.group(2);
            if (!"null".equals(value)) {
                out.put(m.group(1), value);
            }
        }
        return out;
    }

    private static long num(Map<String, String> m, String k, long def) {
        return m.containsKey(k) ? (long) Double.parseDouble(m.get(k)) : def;
    }

    private static String str(String s) {
        return s == null ? "null" : "\"" + s.replace("\"", "") + "\"";
    }
}
