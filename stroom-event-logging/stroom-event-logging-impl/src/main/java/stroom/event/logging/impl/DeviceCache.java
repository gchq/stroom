package stroom.event.logging.impl;

import event.logging.Device;

public interface DeviceCache {

    Device getDeviceForIpAddress(String ipAddress);
}
