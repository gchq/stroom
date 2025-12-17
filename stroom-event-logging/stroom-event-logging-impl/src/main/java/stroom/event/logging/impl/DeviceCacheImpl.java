/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.event.logging.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;

import event.logging.Device;
import event.logging.util.DeviceUtil;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Singleton
public class DeviceCacheImpl implements DeviceCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceCacheImpl.class);
    private static final String CACHE_NAME = "Device Cache";

    private final LoadingStroomCache<String, Device> deviceCache;

    @Inject
    DeviceCacheImpl(final Provider<LoggingConfig> loggingConfigProvider,
                    final CacheManager cacheManager) {
        deviceCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> loggingConfigProvider.get().getDeviceCache(),
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
