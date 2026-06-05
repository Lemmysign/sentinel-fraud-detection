package com.sentinel.rulesengineservice.rules;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.sentinel.sentinelcommons.RedisKeys;
import com.sentinel.sentinelcommons.event.TransactionEvent;
import com.sentinel.rulesengineservice.model.RuleResult;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Geographic anomaly rule — detects physically impossible
 * travel between two consecutive transactions.
 *
 * Detection logic:
 * 1. Look up previous transaction location from Redis
 * 2. Look up current transaction IP location via GeoIP
 *    - Try city-level coordinates first
 *    - Fall back to country centroid if city fails
 *    - Skip rule entirely if both fail
 * 3. Calculate distance between the two locations (Haversine)
 * 4. Calculate time elapsed between transactions
 * 5. Calculate required travel speed
 * 6. If speed exceeds max possible → flag as anomaly
 *
 * Why country centroid fallback?
 * GeoLite2 is inconsistent with African ISP IPs — city-level
 * resolution fails frequently for Nigerian, Ghanaian, and
 * Kenyan IPs. Without a fallback, the rule silently skips
 * a large percentage of African transactions.
 * Country centroid coordinates are always available and
 * sufficient for detecting cross-continent anomalies
 * (Nigeria → London, Nigeria → USA) which are the
 * impossible travel patterns that matter most.
 */
@Slf4j
@Component
public class GeographicAnomalyRule {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${sentinel.rules.geo.database-path}")
    private Resource geoDbResource;

    @Value("${sentinel.rules.geo.max-travel-speed-kmh:800}")
    private double maxTravelSpeedKmh;

    @Value("${sentinel.rules.geo.score-contribution:45}")
    private int scoreContribution;

    private DatabaseReader geoReader;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Country centroid coordinates — geographic center
     * of each country used as fallback when city-level
     * resolution fails.
     *
     * Covers the primary markets Sentinel serves plus
     * major global locations for impossible travel detection.
     *
     * Format: ISO 3166-1 alpha-2 country code → [lat, lon]
     */
    private static final Map<String, double[]> COUNTRY_CENTROIDS =
            Map.ofEntries(
                    // Africa
                    Map.entry("NG", new double[]{9.0820, 8.6753}),   // Nigeria
                    Map.entry("GH", new double[]{7.9465, -1.0232}),  // Ghana
                    Map.entry("KE", new double[]{-0.0236, 37.9062}), // Kenya
                    Map.entry("ZA", new double[]{-30.5595, 22.9375}),// South Africa
                    Map.entry("TZ", new double[]{-6.3690, 34.8888}), // Tanzania
                    Map.entry("UG", new double[]{1.3733, 32.2903}),  // Uganda
                    Map.entry("SN", new double[]{14.4974, -14.4524}),// Senegal
                    Map.entry("CI", new double[]{7.5400, -5.5471}),  // Ivory Coast
                    Map.entry("CM", new double[]{3.8480, 11.5021}),  // Cameroon
                    Map.entry("ET", new double[]{9.1450, 40.4897}),  // Ethiopia
                    // Europe
                    Map.entry("GB", new double[]{55.3781, -3.4360}), // UK
                    Map.entry("DE", new double[]{51.1657, 10.4515}), // Germany
                    Map.entry("FR", new double[]{46.2276, 2.2137}),  // France
                    Map.entry("NL", new double[]{52.1326, 5.2913}),  // Netherlands
                    // Americas
                    Map.entry("US", new double[]{37.0902, -95.7129}),// USA
                    Map.entry("CA", new double[]{56.1304, -106.3468}),// Canada
                    // Asia
                    Map.entry("CN", new double[]{35.8617, 104.1954}),// China
                    Map.entry("IN", new double[]{20.5937, 78.9629}), // India
                    Map.entry("AE", new double[]{23.4241, 53.8478})  // UAE
            );

    public GeographicAnomalyRule(
            RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void initGeoDatabase() {
        try {
            geoReader = new DatabaseReader
                    .Builder(geoDbResource.getInputStream())
                    .build();
            log.info("GeoIP database loaded successfully");
        } catch (IOException e) {
            log.warn("GeoIP database not found — " +
                    "geographic anomaly rule disabled.");
            geoReader = null;
        }
    }

    public RuleResult evaluate(TransactionEvent transaction) {

        if (transaction.getSourceIp() == null
                || transaction.getSourceIp().isBlank()
                || geoReader == null) {
            return RuleResult.notFired("GEOGRAPHIC_ANOMALY");
        }

        try {
            double[] currentLocation = getLocationFromIp(
                    transaction.getSourceIp());

            if (currentLocation == null) {
                log.debug("Geographic anomaly skipped — " +
                                "could not resolve IP {} to any location",
                        transaction.getSourceIp());
                return RuleResult.notFired("GEOGRAPHIC_ANOMALY");
            }

            String redisKey = RedisKeys.GEO_PREFIX
                    + transaction.getAccountId();

            String previousData = redisTemplate
                    .opsForValue().get(redisKey);

            // Store current location for next transaction
            String locationData = currentLocation[0] + ","
                    + currentLocation[1] + ","
                    + transaction.getTimestamp().format(FORMATTER);

            redisTemplate.opsForValue().set(
                    redisKey, locationData,
                    Duration.ofHours(24));

            if (previousData == null) {
                log.debug("Geographic anomaly — first transaction " +
                                "for account {}, storing location",
                        transaction.getAccountId());
                return RuleResult.notFired("GEOGRAPHIC_ANOMALY");
            }

            String[] parts = previousData.split(",");
            if (parts.length < 3) {
                return RuleResult.notFired("GEOGRAPHIC_ANOMALY");
            }

            double prevLat = Double.parseDouble(parts[0]);
            double prevLon = Double.parseDouble(parts[1]);
            LocalDateTime prevTime = LocalDateTime.parse(
                    parts[2], FORMATTER);

            double distanceKm = calculateDistance(
                    prevLat, prevLon,
                    currentLocation[0], currentLocation[1]);

            double hoursElapsed = Duration
                    .between(prevTime, transaction.getTimestamp())
                    .toMinutes() / 60.0;

            if (hoursElapsed <= 0) {
                return RuleResult.notFired("GEOGRAPHIC_ANOMALY");
            }

            double requiredSpeedKmh = distanceKm / hoursElapsed;

            log.debug("Geographic check — account: {}, " +
                            "distance: {} km, elapsed: {} h, " +
                            "required speed: {} km/h",
                    transaction.getAccountId(),
                    String.format("%.1f", distanceKm),
                    String.format("%.2f", hoursElapsed),
                    String.format("%.0f", requiredSpeedKmh));

            if (requiredSpeedKmh > maxTravelSpeedKmh
                    && distanceKm > 100) {

                String explanation = String.format(
                        "Account %s transacted from two locations " +
                                "%.0f km apart within %.0f minutes. " +
                                "Required travel speed: %.0f km/h. " +
                                "Maximum physically possible: %.0f km/h.",
                        transaction.getAccountId(),
                        distanceKm,
                        hoursElapsed * 60,
                        requiredSpeedKmh,
                        maxTravelSpeedKmh);

                log.info("Geographic anomaly fired — account: {}, " +
                                "distance: {}km, speed: {}km/h",
                        transaction.getAccountId(),
                        String.format("%.0f", distanceKm),
                        String.format("%.0f", requiredSpeedKmh));

                return RuleResult.fired("GEOGRAPHIC_ANOMALY",
                        scoreContribution, explanation);
            }

        } catch (Exception e) {
            log.warn("Geographic anomaly check failed — " +
                            "account: {}, error: {}",
                    transaction.getAccountId(), e.getMessage());
        }

        return RuleResult.notFired("GEOGRAPHIC_ANOMALY");
    }
    /**
     * Resolves an IP address to geographic coordinates.
     *
     * Three-tier resolution strategy:
     *
     * Tier 1 — City-level resolution via GeoIP
     * Most precise. Works well for European and American IPs.
     *
     * Tier 2 — Country centroid via GeoIP country code
     * Less precise but sufficient for cross-continent detection.
     * Handles cases where GeoIP knows the country but not the city.
     *
     * Tier 3 — Hardcoded prefix matching for known African ISP ranges
     * GeoLite2 frequently fails entirely for African ISP IPs —
     * returning no city AND no country code. For known Nigerian,
     * Ghanaian, and Kenyan IP prefixes we return the country
     * centroid directly without relying on GeoIP.
     * This ensures African transactions participate in geographic
     * anomaly checking rather than being silently skipped.
     *
     * If all three tiers fail, returns null and the rule is skipped.
     */
    private double[] getLocationFromIp(String ip) {

        // ─── TIER 3 FIRST ────────────────────────────────────────
        // Check known African ISP prefixes before attempting GeoIP.
        // These ranges consistently fail GeoIP lookup — checking
        // upfront avoids unnecessary network calls and ensures
        // these IPs always get a location.
        //
        // Nigerian ISP ranges (MTN, Airtel, Glo, 9mobile)
        if (ip.startsWith("41.58.")
                || ip.startsWith("41.203.")
                || ip.startsWith("41.217.")
                || ip.startsWith("197.210.")
                || ip.startsWith("197.211.")
                || ip.startsWith("105.112.")
                || ip.startsWith("105.113.")
                || ip.startsWith("102.88.")
                || ip.startsWith("102.89.")
                || ip.startsWith("102.90.")) {
            log.debug("Known Nigerian IP prefix detected for {} — " +
                    "using Nigeria centroid", ip);
            return COUNTRY_CENTROIDS.get("NG");
        }

        // Ghanaian ISP ranges (MTN Ghana, AirtelTigo)
        if (ip.startsWith("41.66.")
                || ip.startsWith("154.160.")
                || ip.startsWith("196.201.")) {
            log.debug("Known Ghanaian IP prefix detected for {} — " +
                    "using Ghana centroid", ip);
            return COUNTRY_CENTROIDS.get("GH");
        }

        // Kenyan ISP ranges (Safaricom, Airtel Kenya)
        if (ip.startsWith("41.90.")
                || ip.startsWith("105.163.")
                || ip.startsWith("196.201.2")) {
            log.debug("Known Kenyan IP prefix detected for {} — " +
                    "using Kenya centroid", ip);
            return COUNTRY_CENTROIDS.get("KE");
        }

        // South African ISP ranges (Vodacom, MTN SA, Telkom)
        if (ip.startsWith("41.0.")
                || ip.startsWith("41.13.")
                || ip.startsWith("196.25.")
                || ip.startsWith("102.65.")) {
            log.debug("Known South African IP prefix detected for {} — " +
                    "using South Africa centroid", ip);
            return COUNTRY_CENTROIDS.get("ZA");
        }

        // ─── TIER 1 + TIER 2 — GeoIP lookup ─────────────────────
        try {
            InetAddress address = InetAddress.getByName(ip);
            CityResponse response = geoReader.city(address);

            // Tier 1 — city-level coordinates
            Double lat = response.getLocation().getLatitude();
            Double lon = response.getLocation().getLongitude();

            if (lat != null && lon != null) {
                log.debug("City-level resolution for IP {}: {}, {}",
                        ip, lat, lon);
                return new double[]{lat, lon};
            }

            // Tier 2 — country centroid fallback
            String countryCode = response.getCountry().getIsoCode();
            if (countryCode != null
                    && COUNTRY_CENTROIDS.containsKey(countryCode)) {
                double[] centroid = COUNTRY_CENTROIDS.get(countryCode);
                log.debug("City resolution failed for IP {} — " +
                                "using {} country centroid: {}, {}",
                        ip, countryCode, centroid[0], centroid[1]);
                return centroid;
            }

            log.debug("GeoIP returned no usable location for IP: {} " +
                            "(country: {})", ip,
                    response.getCountry().getIsoCode());

        } catch (IOException | GeoIp2Exception e) {
            log.debug("GeoIP lookup failed entirely for IP: {} — {}",
                    ip, e.getMessage());
        }

        // All three tiers failed
        log.debug("Could not resolve location for IP: {} — " +
                "geographic rule skipped for this transaction", ip);
        return null;
    }

    private double calculateDistance(double lat1, double lon1,
                                     double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}