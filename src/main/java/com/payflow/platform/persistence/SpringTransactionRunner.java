package com.payflow.platform.persistence;

import com.payflow.shared.application.TransactionRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Spring-backed {@link TransactionRunner}: the only place transaction semantics are configured.
 *
 * <ul>
 *   <li><b>Isolation READ COMMITTED</b> (PostgreSQL default). Correctness does not rely on stronger
 *       isolation. It relies on unique constraints (idempotency, one settlement per payment, one journal
 *       per reference) and optimistic version checks (lost updates). SERIALIZABLE would add abort/retry
 *       storms under contention for no additional guarantee here.</li>
 *   <li><b>Propagation REQUIRED</b>: nested calls join the outer transaction.</li>
 *   <li><b>Timeout</b>: a hard upper bound, so a stuck statement cannot pin a pooled connection and row
 *       locks indefinitely. Our transactions contain no remote calls, so they should take milliseconds.</li>
 * </ul>
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate readWrite;
    private final TransactionTemplate readOnly;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager,
                                   @org.springframework.beans.factory.annotation.Value("${payflow.persistence.transaction-timeout:5s}")
                                   Duration timeout) {
        this.readWrite = template(transactionManager, timeout, false);
        this.readOnly = template(transactionManager, timeout, true);
    }

    private static TransactionTemplate template(PlatformTransactionManager tm, Duration timeout, boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(tm);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setTimeout((int) Math.max(1, timeout.toSeconds()));
        template.setReadOnly(readOnly);
        return template;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return readWrite.execute(status -> work.get());
    }

    @Override
    public <T> T readOnly(Supplier<T> work) {
        return readOnly.execute(status -> work.get());
    }
}
