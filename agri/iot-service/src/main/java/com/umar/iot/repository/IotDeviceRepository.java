package com.umar.iot.repository;

import com.umar.enums.iot.DeviceStatus;
import com.umar.enums.iot.DeviceType;
import com.umar.iot.domain.IotDeviceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IotDeviceRepository extends JpaRepository<IotDeviceEntity, String> {
    Optional<IotDeviceEntity> findBySerialNumber(String serialNumber);
    List<IotDeviceEntity> findByPlotIdAndDeviceTypeAndStatusNot(String plotId, DeviceType type, DeviceStatus status);
    List<IotDeviceEntity> findByStatus(DeviceStatus status);
}
