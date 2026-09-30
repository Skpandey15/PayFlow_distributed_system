package com.payflow.payment.application.port.out;

import java.time.Duration;

/**
 * How far behind the publication of this context's outgoing commands is: the age of the oldest command that was
 * committed but has not reached the broker yet (zero when caught up). Saga recovery uses it to tell "the participant
 * did not answer" from "the participant never received the command".
 */
public interface CommandPublicationHealthPort {

    Duration oldestUnpublishedCommandAge();
}
