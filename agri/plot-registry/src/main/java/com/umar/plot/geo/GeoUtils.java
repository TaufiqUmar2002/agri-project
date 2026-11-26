package com.umar.plot.geo;

import com.umar.plot.exception.PlotApiException;
import com.umar.payload.request.plot.RegisterPlotRequest;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java EPSG:4326 geometry validator, geodesic area/width calculator,
 * overlap estimator, and geohash encoder.
 */
public final class GeoUtils {

    private static final String GEOHASH_BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final double METERS_PER_DEG_LAT = 110_574.0;

    private GeoUtils() {}

    public static List<double[]> extractOuterRing(RegisterPlotRequest.GeoJsonPolygon poly, int maxVertices) {
        if (poly == null || poly.getCoordinates() == null || poly.getCoordinates().isEmpty()) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY",
                    "The boundary is not valid", Map.of("detail", "MISSING_RING"));
        }
        List<List<Double>> ringRaw = poly.getCoordinates().getFirst();
        if (ringRaw == null || ringRaw.size() < 4) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY",
                    "The boundary is not valid", Map.of("detail", "TOO_FEW_VERTICES"));
        }
        if (ringRaw.size() > maxVertices) {
            if (maxVertices > 500) {
                throw new PlotApiException(HttpStatus.PAYLOAD_TOO_LARGE, "POLYGON_TOO_COMPLEX",
                        "Simplify the polygon or use the streaming method");
            }
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY",
                    "The boundary is not valid", Map.of("detail", "TOO_MANY_VERTICES"));
        }
        List<double[]> ring = new ArrayList<>(ringRaw.size());
        for (List<Double> pt : ringRaw) {
            if (pt == null || pt.size() < 2) {
                throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY",
                        "The boundary is not valid", Map.of("detail", "INVALID_COORDINATE"));
            }
            double lon = round6(pt.get(0));
            double lat = round6(pt.get(1));
            if (lat < -90 || lat > 90 || lon < -180 || lon > 180) {
                throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY",
                        "The boundary is not valid", Map.of("detail", "OUT_OF_RANGE"));
            }
            ring.add(new double[]{lon, lat});
        }
        double[] first = ring.getFirst();
        double[] last = ring.getLast();
        if (Math.abs(first[0] - last[0]) > 1e-6 || Math.abs(first[1] - last[1]) > 1e-6) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY",
                    "The boundary is not valid", Map.of("detail", "RING_NOT_CLOSED"));
        }
        if (hasSelfIntersection(ring)) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY",
                    "The boundary is not valid", Map.of("detail", "SELF_INTERSECTION"));
        }
        return ring;
    }

    public static RegisterPlotRequest.GeoJsonPolygon buildSquareFromPin(double lat, double lon, double areaHa) {
        double sideM = Math.sqrt(areaHa * 10_000.0);
        double halfLat = (sideM / 2.0) / METERS_PER_DEG_LAT;
        double halfLon = (sideM / 2.0) / (111_320.0 * Math.max(0.1, Math.cos(Math.toRadians(lat))));
        List<List<Double>> ring = List.of(
                List.of(round6(lon - halfLon), round6(lat - halfLat)),
                List.of(round6(lon + halfLon), round6(lat - halfLat)),
                List.of(round6(lon + halfLon), round6(lat + halfLat)),
                List.of(round6(lon - halfLon), round6(lat + halfLat)),
                List.of(round6(lon - halfLon), round6(lat - halfLat))
        );
        return RegisterPlotRequest.GeoJsonPolygon.builder().type("Polygon").coordinates(List.of(ring)).build();
    }

    public static GeoMetrics computeMetrics(List<double[]> ring) {
        double sumLat = 0, sumLon = 0;
        int n = ring.size() - 1;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double lon = ring.get(i)[0], lat = ring.get(i)[1];
            sumLon += lon;
            sumLat += lat;
            minLon = Math.min(minLon, lon);
            maxLon = Math.max(maxLon, lon);
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
        }
        double cLon = round6(sumLon / n);
        double cLat = round6(sumLat / n);
        double mPerDegLon = 111_320.0 * Math.max(0.1, Math.cos(Math.toRadians(cLat)));

        double area2 = 0.0;
        for (int i = 0; i < n; i++) {
            double[] a = ring.get(i);
            double[] b = ring.get((i + 1) % n);
            double ax = (a[0] - cLon) * mPerDegLon;
            double ay = (a[1] - cLat) * METERS_PER_DEG_LAT;
            double bx = (b[0] - cLon) * mPerDegLon;
            double by = (b[1] - cLat) * METERS_PER_DEG_LAT;
            area2 += (ax * by - bx * ay);
        }
        double areaM2 = Math.abs(area2) / 2.0;
        double areaHa = Math.round((areaM2 / 10_000.0) * 100.0) / 100.0;
        double widthX = (maxLon - minLon) * mPerDegLon;
        double widthY = (maxLat - minLat) * METERS_PER_DEG_LAT;
        double minWidthM = Math.round(Math.min(widthX, widthY) * 10.0) / 10.0;
        String bbox = minLon + "," + minLat + "," + maxLon + "," + maxLat;

        return new GeoMetrics(areaHa, areaM2, minWidthM, cLat, cLon, bbox,
                geohash(cLat, cLon, 5), geohash(cLat, cLon, 7));
    }

    public static double computeOverlapPercent(List<double[]> candidate, List<double[]> existing, double inwardBufferM) {
        GeoMetrics cm = computeMetrics(candidate);
        if (cm.areaM2() <= 0) return 0.0;
        List<double[]> buffered = shrinkTowardCentroid(candidate, cm.centroidLon(), cm.centroidLat(), inwardBufferM);
        // Deterministic 20x20 grid sampling inside candidate bbox
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        for (double[] p : buffered) {
            minLon = Math.min(minLon, p[0]); maxLon = Math.max(maxLon, p[0]);
            minLat = Math.min(minLat, p[1]); maxLat = Math.max(maxLat, p[1]);
        }
        int steps = 20;
        int insideCandidate = 0;
        int insideBoth = 0;
        for (int i = 0; i < steps; i++) {
            double lon = minLon + (i + 0.5) * (maxLon - minLon) / steps;
            for (int j = 0; j < steps; j++) {
                double lat = minLat + (j + 0.5) * (maxLat - minLat) / steps;
                if (pointInPolygon(lon, lat, buffered)) {
                    insideCandidate++;
                    if (pointInPolygon(lon, lat, existing)) {
                        insideBoth++;
                    }
                }
            }
        }
        if (insideCandidate == 0) return 0.0;
        return Math.round(((insideBoth * 100.0) / insideCandidate) * 100.0) / 100.0;
    }

    public static boolean intersects(List<double[]> a, List<double[]> b) {
        return computeOverlapPercent(a, b, 0.0) > 0.0 || computeOverlapPercent(b, a, 0.0) > 0.0;
    }

    public static boolean pointInPolygon(double lon, double lat, List<double[]> ring) {
        boolean inside = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring.get(i)[0], yi = ring.get(i)[1];
            double xj = ring.get(j)[0], yj = ring.get(j)[1];
            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lon < (xj - xi) * (lat - yi) / ((yj - yi) == 0 ? 1e-12 : (yj - yi)) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    public static List<double[]> shrinkTowardCentroid(List<double[]> ring, double cLon, double cLat, double bufferM) {
        if (bufferM <= 0) return ring;
        double mPerDegLon = 111_320.0 * Math.max(0.1, Math.cos(Math.toRadians(cLat)));
        List<double[]> out = new ArrayList<>(ring.size());
        for (double[] p : ring) {
            double dx = (p[0] - cLon) * mPerDegLon;
            double dy = (p[1] - cLat) * METERS_PER_DEG_LAT;
            double dist = Math.hypot(dx, dy);
            if (dist <= bufferM) {
                out.add(new double[]{cLon, cLat});
            } else {
                double scale = (dist - bufferM) / dist;
                out.add(new double[]{cLon + (dx * scale) / mPerDegLon, cLat + (dy * scale) / METERS_PER_DEG_LAT});
            }
        }
        return out;
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String geohash(double lat, double lon, int precision) {
        double[] latRange = {-90.0, 90.0};
        double[] lonRange = {-180.0, 180.0};
        boolean isEven = true;
        int bit = 0, ch = 0;
        StringBuilder sb = new StringBuilder(precision);
        while (sb.length() < precision) {
            double mid;
            if (isEven) {
                mid = (lonRange[0] + lonRange[1]) / 2.0;
                if (lon >= mid) { ch |= (1 << (4 - bit)); lonRange[0] = mid; }
                else { lonRange[1] = mid; }
            } else {
                mid = (latRange[0] + latRange[1]) / 2.0;
                if (lat >= mid) { ch |= (1 << (4 - bit)); latRange[0] = mid; }
                else { latRange[1] = mid; }
            }
            isEven = !isEven;
            if (bit < 4) bit++;
            else {
                sb.append(GEOHASH_BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }
        return sb.toString();
    }

    private static boolean hasSelfIntersection(List<double[]> ring) {
        int segs = ring.size() - 1;
        for (int i = 0; i < segs; i++) {
            for (int j = i + 2; j < segs; j++) {
                if (i == 0 && j == segs - 1) continue;
                if (segmentsIntersect(ring.get(i), ring.get(i + 1), ring.get(j), ring.get(j + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(double[] a, double[] b, double[] c, double[] d) {
        double d1 = cross(c, d, a);
        double d2 = cross(c, d, b);
        double d3 = cross(a, b, c);
        double d4 = cross(a, b, d);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private static double cross(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000.0) / 1_000_000.0;
    }

    public record GeoMetrics(
            double areaHa, double areaM2, double minWidthM,
            double centroidLat, double centroidLon, String bbox,
            String geohash5, String geohash7) {}
}
