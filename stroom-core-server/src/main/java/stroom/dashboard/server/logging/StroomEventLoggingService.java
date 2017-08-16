/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.server.logging;

import event.logging.Device;
import event.logging.Event;
import event.logging.Event.EventDetail;
import event.logging.Event.EventSource;
import event.logging.Event.EventTime;
import event.logging.EventLoggingService;
import event.logging.System;
import event.logging.User;
import event.logging.impl.DefaultEventLoggingService;
import event.logging.util.DeviceUtil;
import event.logging.util.EventLoggingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.security.SecurityContext;
import stroom.servlet.HttpServletRequestHolder;
import stroom.util.BuildInfoUtil;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

@Component
public class StroomEventLoggingService extends DefaultEventLoggingService implements EventLoggingService {
    /**
     * Logger - should not be used for event logs
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomEventLoggingService.class);

    private static final String SYSTEM = "Stroom";
    private static final String ENVIRONMENT = "";
    private static final String GENERATOR = "StroomEventLoggingService";

    private volatile boolean obtainedDevice;
    private volatile Device storedDevice;

    @Resource
    private transient HttpServletRequestHolder httpServletRequestHolder;
    @Resource
    private SecurityContext security;

    @Override
    public Event createEvent() {
        // Get the current request.
        final HttpServletRequest request = httpServletRequestHolder.get();

        // Create event time.
        final EventTime eventTime = new EventTime();
        eventTime.setTimeCreated(new Date());

        // Get device.
        final Device device = getDevice(request);

        // Get client.
        final Device client = getClient(request);

        // Get user.
        final User user = getUser();

        // Create system.
        final System system = new System();
        system.setName(SYSTEM);
        system.setEnvironment(ENVIRONMENT);
        system.setVersion(BuildInfoUtil.getBuildVersion());

        // Create event source.
        final EventSource eventSource = new EventSource();
        eventSource.setSystem(system);
        eventSource.setGenerator(GENERATOR);
        eventSource.setDevice(device);
        eventSource.setClient(client);
        eventSource.setUser(user);

        // Create event.
        final Event event = super.createEvent();
        event.setEventTime(eventTime);
        event.setEventSource(eventSource);

        return event;
    }

    public Event createAction(final String typeId, final String description) {
        final Event event = createEvent();

        final EventDetail eventDetail = EventLoggingUtil.createEventDetail(typeId, description);
        event.setEventDetail(eventDetail);

        return event;
    }

    private Device getDevice(final HttpServletRequest request) {
        // Get stored device info.
        final Device storedDevice = obtainStoredDevice(request);

        // We need to copy the stored device as users may make changes to the
        // returned object that might not be thread safe.
        Device device = null;
        if (storedDevice != null) {
            device = copyDevice(storedDevice, new Device());
        }

        return device;
    }

    private Device getClient(final HttpServletRequest request) {
        if (request != null) {
            try {
                String ip = request.getRemoteAddr();
                ip = DeviceUtil.getValidIP(ip);

                if (ip != null) {
                    InetAddress inetAddress = null;
                    try {
                        inetAddress = InetAddress.getByName(ip);
                    } catch (final UnknownHostException e) {
                        LOGGER.warn("Problem getting client InetAddress", e);
                    }

                    Device client = null;
                    if (inetAddress != null) {
                        client = DeviceUtil.createDeviceFromInetAddress(inetAddress);
                    } else {
                        client = new Device();
                    }

                    client.setIPAddress(ip);
                    return client;
                }
            } catch (final Exception e) {
                LOGGER.warn("Problem getting client IP address and host name", e);
            }
        }

        return null;
    }

    private User getUser() {
        try {
            final String userId = security.getUserId();
            if (userId != null) {
                final User user = new User();
                user.setId(userId);
                return user;
            }
        } catch (final Exception e) {
            LOGGER.warn("Problem getting current user", e);
        }

        return null;
    }

    private synchronized Device obtainStoredDevice(final HttpServletRequest request) {
        if (!obtainedDevice) {
            // First try and get the local server IP address and host name.
            InetAddress inetAddress = null;
            try {
                inetAddress = InetAddress.getLocalHost();
            } catch (final UnknownHostException e) {
                LOGGER.warn("Problem getting device from InetAddress", e);
            }

            if (inetAddress != null) {
                storedDevice = DeviceUtil.createDeviceFromInetAddress(inetAddress);
            } else {
                // Make final attempt to set with request if we have one and
                // haven't been able to set IP and host name already.
                if (request != null) {
                    final String ip = DeviceUtil.getValidIP(request.getLocalAddr());
                    if (ip != null) {
                        try {
                            inetAddress = InetAddress.getByName(ip);
                        } catch (final UnknownHostException e) {
                            LOGGER.warn("Problem getting client InetAddress", e);
                        }

                        if (inetAddress != null) {
                            storedDevice = DeviceUtil.createDeviceFromInetAddress(inetAddress);
                        } else {
                            storedDevice = new Device();
                        }

                        storedDevice.setIPAddress(ip);
                    }
                }
            }

            obtainedDevice = true;
        }

        return storedDevice;
    }

    private Device copyDevice(final Device source, final Device dest) {
        dest.setIPAddress(source.getIPAddress());
        dest.setHostName(source.getHostName());
        dest.setMACAddress(source.getMACAddress());
        return dest;
    }
}
