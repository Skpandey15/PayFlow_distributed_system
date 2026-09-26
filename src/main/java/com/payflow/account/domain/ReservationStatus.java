package com.payflow.account.domain;

/**
 * <pre>
 *  RESERVED ──capture──▶ CAPTURED
 *     └──────release───▶ RELEASED
 *  REJECTED   (reservation refused: insufficient funds / inactive account)
 *  RELEASED   also created directly as a tombstone when a release overtakes its reservation
 * </pre>
 */
public enum ReservationStatus {
    RESERVED,
    CAPTURED,
    RELEASED,
    REJECTED
}
