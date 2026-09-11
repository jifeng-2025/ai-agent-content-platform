package com.yupi.template.runtime;

/** Optional provider-specific capability. Production synchronous providers expose neither query nor deduplication.
 * A provider adapter must only advertise capabilities its real API guarantees. */
public interface RuntimeProvider {
 boolean supports(String provider);
 record Result(String status,String jobId,String result,Long actualCostMicros) {}
 Result submit(String requestId,String payload) throws Exception;
 Result query(String requestId,String jobId) throws Exception;
 boolean idempotentSubmit();
}
