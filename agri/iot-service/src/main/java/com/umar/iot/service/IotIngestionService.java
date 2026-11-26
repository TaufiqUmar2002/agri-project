package com.umar.iot.service;

import com.umar.enums.iot.ConnectivityStatus;
import com.umar.enums.iot.DeviceStatus;
import com.umar.iot.client.IotExternalClients;
import com.umar.iot.config.IotProperties;
import com.umar.iot.domain.IotDeviceEntity;
import com.umar.iot.domain.IotReadingEntity;
import com.umar.iot.domain.IotRejectedReadingEntity;
import com.umar.iot.repository.IotDeviceRepository;
import com.umar.iot.repository.IotReadingRepository;
import com.umar.iot.repository.IotRejectedReadingRepository;
import com.umar.iot.service.support.IotSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IotIngestionService {

    private final IotDeviceRepository deviceRepository;
    private final IotReadingRepository readingRepository;
    private final IotRejectedReadingRepository rejectedRepository;
    private final IotExternalClients clients;
    private final IotSupportService support;
    private final IotProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public String ingestReading(String topicDeviceId, String clientCertCn, Instant observedAt,
                                String channel, double rawValue, String unit, Integer battery, Integer rssi) {
        if (!Objects.equals(topicDeviceId, clientCertCn)) {
            return "INCIDENT:IDENTITY_MISMATCH";
        }
        IotDeviceEntity dev = deviceRepository.findById(topicDeviceId).orElse(null);
        if (dev == null) return "DROP:UNKNOWN_DEVICE";
        if (dev.getStatus() == DeviceStatus.RETIRED || dev.getStatus() == DeviceStatus.SUSPENDED) {
            return "DROP:DEVICE_NOT_ACTIVE";
        }

        Instant now = Instant.now();
        Instant adjustedTime = observedAt.plusSeconds(dev.getClockOffsetSeconds());
        if (adjustedTime.isAfter(now.plus(10, ChronoUnit.MINUTES))) {
            saveRejection(dev.getDeviceId(), channel, rawValue, "FUTURE_TIMESTAMP", now);
            return "REJECT:FUTURE_TIMESTAMP";
        }
        if (adjustedTime.isBefore(now.minus(7, ChronoUnit.DAYS))) {
            saveRejection(dev.getDeviceId(), channel, rawValue, "READING_TOO_OLD", now);
            return "REJECT:READING_TOO_OLD";
        }

        String stdChannel = "rain.tips".equals(channel) ? "rain.mm" : channel;
        double mmPerTip = "rain.tips".equals(channel) ? 0.25 : 1.0;
        Map<String, Object> cal = readMap(dev.getCalibrationJson());
        double scale = ((Number) cal.getOrDefault("scale", 1.0)).doubleValue();
        double offset = ((Number) cal.getOrDefault("offset", 0.0)).doubleValue();
        double normalized = Math.round(((rawValue * mmPerTip) * scale + offset) * 10.0) / 10.0;

        if ("rain.mm".equals(stdChannel) && (normalized < 0 || normalized > 200)) {
            saveRejection(dev.getDeviceId(), stdChannel, rawValue, "OUT_OF_PHYSICAL_RANGE", now);
            return "REJECT:OUT_OF_PHYSICAL_RANGE";
        }

        String ingestId = "obs_" + clients.sha256(dev.getDeviceId() + "|" + stdChannel + "|" + adjustedTime).substring(0, 12);
        if (readingRepository.existsById(ingestId)) {
            return "SKIP:DUPLICATE_READING";
        }

        List<String> flags = new ArrayList<>();
        boolean lateArrival = Duration.between(adjustedTime, now).toMinutes() > 30;
        if (lateArrival) flags.add("LATE_ARRIVAL");
        if (dev.getCalibrationVersion() == 1) flags.add("CALIBRATION_DEFAULT");
        if (battery != null && battery < 30) flags.add("LOW_BATTERY_READING");

        boolean wasOffline = dev.getConnectivity() == ConnectivityStatus.OFFLINE;
        dev.setStatus(DeviceStatus.ACTIVE);
        dev.setConnectivity(ConnectivityStatus.ONLINE);
        dev.setLastSeenAt(now);
        dev.setLastReadingAt(now);
        if (battery != null) dev.setBatteryPercent(battery);
        if (rssi != null) dev.setRssi(rssi);
        dev.setVersionNum(dev.getVersionNum() + 1);
        deviceRepository.save(dev);

        readingRepository.save(IotReadingEntity.builder()
                .ingestId(ingestId).deviceId(dev.getDeviceId()).plotId(dev.getPlotId())
                .districtCode(dev.getDistrictCode()).channel(stdChannel)
                .rawValue(rawValue).metricValue(normalized).unit("rain.mm".equals(stdChannel) ? "mm" : Objects.toString(unit, "%"))
                .observedAt(adjustedTime).ingestedAt(now).qualityFlagsCsv(String.join(",", flags))
                .calibrationVersion(dev.getCalibrationVersion()).revision(0).lateArrival(lateArrival).build());

        if (dev.getPlotId() == null) {
            return "STORE:DEVICE_UNBOUND";
        }

        support.enqueue(properties.getKafka().getObservationTopic(), "observation.sensor",
                Objects.toString(dev.getDistrictCode(), dev.getDeviceId()), Map.ofEntries(
                        Map.entry("ingestId", ingestId),
                        Map.entry("sourceId", "rain.mm".equals(stdChannel) ? "src_iot_rain_gauge" : "src_iot_soil_moisture"),
                        Map.entry("deviceId", dev.getDeviceId()),
                        Map.entry("unitId", dev.getPlotId()),
                        Map.entry("districtCode", Objects.toString(dev.getDistrictCode(), "")),
                        Map.entry("channel", stdChannel),
                        Map.entry("value", normalized),
                        Map.entry("unit", "mm"),
                        Map.entry("observedAt", adjustedTime.toString()),
                        Map.entry("ingestedAt", now.toString()),
                        Map.entry("calibrationVersion", dev.getCalibrationVersion()),
                        Map.entry("revision", 0),
                        Map.entry("lateArrival", lateArrival)));

        if (wasOffline) {
            support.enqueue(properties.getKafka().getIotTopic(), "device.online", dev.getDeviceId(), Map.of(
                    "eventType", "device.online", "deviceId", dev.getDeviceId(),
                    "plotId", dev.getPlotId(), "backAt", now.toString()));
        }
        return flags.isEmpty() ? "PUBLISHED:" + ingestId : "FLAG:" + ingestId;
    }

    @Transactional
    public String ingestStatus(String topicDeviceId, String clientCertCn, Integer battery, Integer rssi) {
        if (!Objects.equals(topicDeviceId, clientCertCn)) return "INCIDENT:IDENTITY_MISMATCH";
        IotDeviceEntity dev = deviceRepository.findById(topicDeviceId).orElse(null);
        if (dev == null) return "DROP:UNKNOWN_DEVICE";
        if (dev.getStatus() == DeviceStatus.RETIRED) return "DROP:DEVICE_RETIRED";

        Instant now = Instant.now();
        boolean wasOffline = dev.getConnectivity() == ConnectivityStatus.OFFLINE;
        dev.setStatus(DeviceStatus.ACTIVE);
        dev.setConnectivity(ConnectivityStatus.ONLINE);
        dev.setLastSeenAt(now);
        if (battery != null) dev.setBatteryPercent(battery);
        if (rssi != null) dev.setRssi(rssi);
        deviceRepository.save(dev);

        if (wasOffline) {
            support.enqueue(properties.getKafka().getIotTopic(), "device.online", dev.getDeviceId(), Map.of(
                    "eventType", "device.online", "deviceId", dev.getDeviceId(),
                    "plotId", Objects.toString(dev.getPlotId(), ""), "backAt", now.toString()));
        }
        return (battery != null && battery < 30) ? "FLAG:BATTERY_LOW" : "UPDATED";
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public int detectOfflineDevices() {
        Instant now = Instant.now();
        int marked = 0;
        for (IotDeviceEntity d : deviceRepository.findByStatus(DeviceStatus.ACTIVE)) {
            long thresholdSec = Math.max(1800L, Math.min(21600L, d.getExpectedIntervalSeconds() * 3L));
            if (d.getLastSeenAt() != null && d.getLastSeenAt().plusSeconds(thresholdSec).isBefore(now)
                    && d.getConnectivity() != ConnectivityStatus.OFFLINE) {
                d.setConnectivity(ConnectivityStatus.OFFLINE);
                d.setVersionNum(d.getVersionNum() + 1);
                deviceRepository.save(d);
                support.enqueue(properties.getKafka().getIotTopic(), "device.offline", d.getDeviceId(), Map.of(
                        "eventType", "device.offline", "deviceId", d.getDeviceId(),
                        "plotId", Objects.toString(d.getPlotId(), ""),
                        "districtCode", Objects.toString(d.getDistrictCode(), ""),
                        "lastSeenAt", d.getLastSeenAt().toString(),
                        "offlineSince", now.toString(), "reminder", false));
                marked++;
            }
        }
        return marked;
    }

    private void saveRejection(String deviceId, String ch, double val, String reason, Instant now) {
        rejectedRepository.save(IotRejectedReadingEntity.builder()
                .rejectId("rej_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .deviceId(deviceId).channel(ch).rawValue(val).reasonCode(reason).receivedAt(now).build());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try { return objectMapper.readValue(json, Map.class); } catch (Exception e) { return Map.of(); }
    }
}
