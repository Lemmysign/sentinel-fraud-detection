package com.sentinel.sentinelcommons;


/**
 * Centralised Redis key constants shared across services.
 *
 * Why centralise Redis keys?
 * The rules-engine-service READS recipient risk counters.
 * The case-management-service WRITES recipient risk counters.
 * Both must use identical key formats — if one changes,
 * the other breaks silently at runtime.
 *
 * Defining keys here means one change updates both services.
 * The compiler catches mismatches instead of Redis returning
 * zero at 2am during a fraud incident.
 */
public final class RedisKeys {

    private RedisKeys() {
        // Utility class — no instantiation
    }

    // =====================================================
    // VELOCITY CHECKS (rules-engine-service)
    // Key: velocity:{accountId}
    // Type: String (integer counter)
    // TTL:  velocity window seconds (e.g. 60s)
    // =====================================================
    public static final String VELOCITY_PREFIX = "velocity:";

    // =====================================================
    // DEVICE TRACKING (rules-engine-service)
    // Key: devices:{accountId}
    // Type: Set of device ID strings
    // TTL:  none — device history accumulates
    // =====================================================
    public static final String DEVICE_PREFIX = "devices:";

    // =====================================================
    // GEOGRAPHIC TRACKING (rules-engine-service)
    // Key: geo:{accountId}
    // Type: String ("lat,lon,timestamp")
    // TTL:  24 hours
    // =====================================================
    public static final String GEO_PREFIX = "geo:";

    // =====================================================
    // BEHAVIORAL BASELINE (rules-engine-service)
    // Key: baseline:sum:{accountId}
    // Key: baseline:count:{accountId}
    // Type: String (decimal / integer)
    // TTL:  none — baseline accumulates over time
    // =====================================================
    public static final String BASELINE_SUM_PREFIX = "baseline:sum:";
    public static final String BASELINE_COUNT_PREFIX = "baseline:count:";

    // =====================================================
    // RECIPIENT RISK (rules-engine reads, case-mgmt writes)
    // Key: recipient:risk:{merchantId}
    // Type: String (integer counter)
    // TTL:  none — fraud history is permanent
    // =====================================================
    public static final String RECIPIENT_RISK_PREFIX = "recipient:risk:";
}