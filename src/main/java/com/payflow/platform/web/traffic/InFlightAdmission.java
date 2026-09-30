package com.payflow.platform.web.traffic;

/**
 * Work-in-progress window for payment admission (review P-2). By Little's law, completion time = work in progress /
 * throughput, so keeping the work in progress under a limit bounds how long an accepted payment waits, whatever the
 * offered load.
 *
 * <p>A window, not a gate or a probability. Each sample grants {@link #SHARE_PERCENT} % of the free room
 * ({@code limit - workInProgress}) as admission credits until the next sample, so admissions can never outrun the
 * room, however fast requests arrive. Two designs failed first:
 * <ul>
 *   <li>A threshold on consumer lag sampled every 5 s oscillated (admit a burst, close, drain, reopen;
 *       TUNING-RESULTS §9).</li>
 *   <li>A linear shedding ramp from 80 % to 100 % of the limit, sampled every second, still oscillated at 150
 *       requests/s. One second of full admission overshot the whole 100-payment ramp.</li>
 * </ul>
 * Granting only half the room damps the loop, and it keeps two replicas that sample the same work in progress from
 * jointly overshooting the limit.
 */
final class InFlightAdmission {

    static final int SHARE_PERCENT = 50;

    private final long limit;

    InFlightAdmission(long limit) {
        this.limit = limit;
    }

    boolean enabled() {
        return limit > 0;
    }

    /** Admission credits until the next sample: half of the free room, never negative. */
    long credits(long workInProgress) {
        return Math.max(0, (limit - workInProgress) * SHARE_PERCENT / 100);
    }
}
