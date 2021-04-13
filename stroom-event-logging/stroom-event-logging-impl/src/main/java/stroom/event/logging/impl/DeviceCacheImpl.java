package stroom.event.logging.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;

import event.logging.Device;
import event.logging.util.DeviceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DeviceCacheImpl implements DeviceCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceCacheImpl.class);
    private static final String CACHE_NAME = "Device Cache";

    private final ICache<String, Device> deviceCache;

    @Inject
    DeviceCacheImpl(final LoggingConfig loggingConfig,
                    final CacheManager cacheManager) {
        deviceCache = cacheManager.create(CACHE_NAME,
                loggingConfig::getDeviceCache,
                this::createDevice,
                this::destroyDevice);
    }

    @Override
    public Device getDeviceForIpAddress(final String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        return deviceCache.get(ipAddress);
    }

    private Device createDevice(final String ipAddress) {
        Device device = null;
        try {
            final String ip = DeviceUtil.getValidIP(ipAddress);
            if (ip != null) {
                InetAddress inetAddress = null;
                try {
                    inetAddress = InetAddress.getByName(ip);
                } catch (final UnknownHostException e) {
                    LOGGER.warn("Problem getting InetAddress for ip=" +
                            ip +
                            " (" +
                            e.getMessage() +
                            ")", e);
                }

                if (inetAddress != null) {
                    device = DeviceUtil.createDeviceFromInetAddress(inetAddress);
                } else {
                    device = new Device();
                }
                device.setIPAddress(ip);
            }
        } catch (final RuntimeException e) {
            LOGGER.warn("Problem getting IP address and host name for ip=" +
                    ipAddress +
                    " (" +
                    e.getMessage() +
                    ")", e);
        }

        return device;
    }

    private void destroyDevice(final String ipAddress, final Device value) {
        LOGGER.debug("destroy: " + ipAddress);
    }
}
