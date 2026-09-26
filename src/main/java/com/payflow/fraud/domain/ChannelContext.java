package com.payflow.fraud.domain;

/**
 * Device/network context of the checkout that originated the payment. All fields are optional. This is
 * the kind of loosely-structured, evolving evidence that motivates storing assessments as documents.
 */
public record ChannelContext(String deviceId, String ipAddress, String userAgent, String countryCode) {

    public static final ChannelContext EMPTY = new ChannelContext(null, null, null, null);

    public boolean hasDevice() {
        return deviceId != null && !deviceId.isBlank();
    }
}
