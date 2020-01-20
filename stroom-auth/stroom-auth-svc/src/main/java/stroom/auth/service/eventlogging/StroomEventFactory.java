package stroom.auth.service.eventlogging;

import event.logging.Device;
import event.logging.Event;
import event.logging.EventLoggingService;
import event.logging.System;
import event.logging.User;
import event.logging.impl.DefaultEventLoggingService;
import event.logging.util.DeviceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.auth.config.EventLoggingConfig;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;

/**
 * A factory to assist with creating Stroom logging events.
 */
class StroomEventFactory extends DefaultEventLoggingService implements EventLoggingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomEventLoggingService.class);
    private static final String GENERATOR = "EventLoggingService";

    private volatile boolean obtainedDevice;
    private volatile Device storedDevice;
    private EventLoggingConfig config;

    StroomEventFactory (EventLoggingConfig config) {
        this.setValidate(true);
        this.config = config;
    }

    Event createEvent(final HttpServletRequest request, String usersEmail) {
        // Create event time.
        final Event.EventTime eventTime = new Event.EventTime();
        eventTime.setTimeCreated(new Date());

        // Get device.
        final Device device = getDevice(request);

        // Get client.
        final Device client = getClient(request);

        // Get user.
        final User user = new User();
//        user.setDomain(serviceUser.); // TODO What's the domain?
        user.setEmailAddress(usersEmail);
//        UserDetails userDetails = new UserDetails(); // TODO What's this for?
//        user.setUserDetails();

        // Create system.
        final System system = new System();
        system.setName(config.getSystem());
        system.setEnvironment(config.getEnvironment());
        system.setDescription(config.getDescription());
        system.setVersion(config.getBuildVersion());

        // Create event source.
        final Event.EventSource eventSource = new Event.EventSource();
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
            } catch (final RuntimeException e) {
                LOGGER.warn("Problem getting client IP address and host name", e);
            }
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
