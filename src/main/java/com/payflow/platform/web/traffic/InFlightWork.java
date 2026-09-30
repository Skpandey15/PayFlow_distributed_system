package com.payflow.platform.web.traffic;

/**
 * How much accepted work PayFlow is still processing: the admission-control signal (review P-2). Implemented by the
 * context that owns the work (the payment saga), so the platform never reads another context's tables.
 */
public interface InFlightWork {

    long count();
}
