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

package stroom.event.logging.impl;

import stroom.activity.api.CurrentActivity;
import stroom.event.logging.api.LoggedResult;
import stroom.event.logging.api.PurposeUtil;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.util.shared.BuildInfo;

import event.logging.BaseOutcome;
import event.logging.Device;
import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventDetail;
import event.logging.EventDetail.Builder;
import event.logging.EventSource;
import event.logging.EventTime;
import event.logging.HasOutcome;
import event.logging.SystemDetail;
import event.logging.User;
import event.logging.impl.DefaultEventLoggingService;
import event.logging.util.DeviceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

@Singleton
public class StroomEventLoggingServiceImpl extends DefaultEventLoggingService implements StroomEventLoggingService {
    /**
     * Logger - should not be used for event logs
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomEventLoggingServiceImpl.class);

    private static final String SYSTEM = "Stroom";
    private static final String ENVIRONMENT = "";
    private static final String GENERATOR = "StroomEventLoggingService";

    private volatile boolean obtainedDevice;
    private volatile Device storedDevice;

    private final SecurityContext securityContext;
    private final Provider<HttpServletRequest> httpServletRequestProvider;
    private final CurrentActivity currentActivity;
    private final Provider<BuildInfo> buildInfoProvider;
    private final Map<Class<? extends EventAction>, Optional<Function<EventAction, BaseOutcome>>> outcomeFactoryMap = new ConcurrentHashMap<>();

    @Inject
    StroomEventLoggingServiceImpl(final SecurityContext securityContext,
                                  final Provider<HttpServletRequest> httpServletRequestProvider,
                                  final CurrentActivity currentActivity,
                                  final Provider<BuildInfo> buildInfoProvider) {
        this.securityContext = securityContext;
        this.httpServletRequestProvider = httpServletRequestProvider;
        this.currentActivity = currentActivity;
        this.buildInfoProvider = buildInfoProvider;
    }

    @Override
    public void log(final Event event) {
        try {
            super.log(event);
        } catch (Exception e) {
            // Swallow the exception so failure to log does not prevent the action being logged
            // from succeeding
            LOGGER.error("Error logging event", e);
        }
    }

    @Override
    public Event createEvent() {
        return buildEvent().build();
    }

    @Override
    public Event.Builder<Void> buildEvent() {
        // Get the current request.
        final HttpServletRequest request = getRequest();

        return super.buildEvent()
                .withEventTime(EventTime.builder()
                        .withTimeCreated(new Date())
                        .build())
                .withEventSource(EventSource.builder()
                        .withSystem(SystemDetail.builder()
                                .withName(SYSTEM)
                                .withEnvironment(ENVIRONMENT)
                                .withVersion(buildInfoProvider.get().getBuildVersion())
                                .build())
                        .withGenerator(GENERATOR)
                        .withDevice(getClient(request))
                        .withClient(getClient(request))
                        .withUser(getUser())
                        .build());
    }

    public Event createSkeletonEvent(final String typeId, final String description) {
        return createSkeletonEvent(typeId, description, null);
    }

    @Override
    public Event createSkeletonEvent(final String typeId,
                                     final String description,
                                     final Consumer<Builder<Void>> eventDetailBuilderConsumer) {
        final Builder<Void> eventDetailBuilder = EventDetail.builder()
                .withTypeId(typeId)
                .withDescription(description)
                .withPurpose(PurposeUtil.create(currentActivity.getActivity()));

        if (eventDetailBuilderConsumer != null) {
            eventDetailBuilderConsumer.accept(eventDetailBuilder);
        }

        return buildEvent()
                .withEventDetail(eventDetailBuilder.build())
                .build();
    }

    @Override
    public void log(final String typeId,
                    final String description,
                    final Consumer<Builder<Void>> eventDetailBuilderConsumer) {

        super.log(createSkeletonEvent(typeId, description, eventDetailBuilderConsumer));
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

    private User getUser() {
        try {
            final String userId;
            if (securityContext.isProcessingUser()) {
                // We are running as proc user so try and get the OS user,
                // though that may just be a shared account.
                // This is useful where a CLI command is being used
                final String osUser = System.getProperty("user.name");

                userId = osUser != null
                        ? osUser
                        : securityContext.getUserId();
            } else {
                userId = securityContext.getUserId();
            }

            if (userId != null) {
                return User.builder()
                        .withId(userId)
                        .build();
            }
        } catch (final RuntimeException e) {
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

    private HttpServletRequest getRequest() {
        if (httpServletRequestProvider != null) {
            return httpServletRequestProvider.get();
        }
        return null;
    }

    @Override
    public <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
            final String eventTypeId,
            final String description,
            final T_EVENT_ACTION eventAction,
            final Function<T_EVENT_ACTION, LoggedResult<T_RESULT, T_EVENT_ACTION>> loggedWork,
            final BiFunction<T_EVENT_ACTION, Throwable, T_EVENT_ACTION> exceptionHandler) {

        Objects.requireNonNull(eventAction);
        Objects.requireNonNull(loggedWork);

        final T_RESULT result;
        Event event = null;
        try {
            event = createSkeletonEvent(
                    eventTypeId,
                    description,
                    eventDetailBuilder -> eventDetailBuilder
                            .withEventAction(eventAction));
        } catch (Exception e) {
            // Swallow the exception so failure to log does not prevent the action being logged
            // from succeeding
            LOGGER.error("Error creating skeleton event", e);
        }

        if (event != null) {
            try {
                // Performe the callers work, allowing them to provide a new EventAction based on the
                // result of the work e.g. if they are updating a record, they can capture the before state
                final LoggedResult<T_RESULT, T_EVENT_ACTION> loggedResult = loggedWork.apply(eventAction);

                // Set the new EventAction onto the existing event
                setActionOnEvent(event, loggedResult.getEventAction());
                log(event);
                result = loggedResult.getResult();
            } catch (Throwable e) {
                if (exceptionHandler != null) {
                    try {
                        // Allow caller to provide a new EventAction based on the exception
                        T_EVENT_ACTION newEventAction = exceptionHandler.apply(eventAction, e);
                        setActionOnEvent(event, newEventAction);
                    } catch (Exception exception) {
                        LOGGER.error( "Error running exception handler. " +
                                        "Swallowing exception and rethrowing original exception", e);
                    }
                } else {
                    // No handler so see if we can add an outcome
                    if (eventAction instanceof HasOutcome) {
                        addFailureOutcome(e, eventAction);
                    }
                }
                log(event);
                throw e;
            }
        } else {
            // We failed to create an event so just do the work with no logging.
            result = loggedWork.apply(eventAction).getResult();
        }

        return result;
    }

    private <T_EVENT_ACTION extends EventAction> void setActionOnEvent(final Event event, final T_EVENT_ACTION newEventAction) {
        if (event.getEventDetail() != null) {
            event.getEventDetail()
                    .setEventAction(newEventAction);
        } else {
            LOGGER.error("Unable to set event action as event detail is null");
        }
    }

    private void addFailureOutcome(final Throwable e, final EventAction eventAction) {
        try {
            final HasOutcome hasOutcome = (HasOutcome) eventAction;
            BaseOutcome baseOutcome = hasOutcome.getOutcome();

            if (baseOutcome == null) {
                // eventAction has no outcome so we need to create one on it
                baseOutcome = createBaseOutcome(eventAction)
                        .orElse(null);
            }

            if (baseOutcome == null) {
                LOGGER.error("Unable to set outcome on {}", eventAction.getClass().getName());
            } else {
                baseOutcome.setSuccess(false);
                baseOutcome.setDescription(e.getMessage() != null
                        ? e.getMessage()
                        : e.getClass().getName());
            }
        } catch (Exception exception) {
            LOGGER.error("Unable to add failure outcome to {}", eventAction.getClass().getName(), e);
        }
    }

    private Optional<BaseOutcome> createBaseOutcome(final EventAction eventAction) {
        // We need to call setOutcome on eventAction but we don't know what sub class of
        // BaseOutcome it is so need to use reflection to find out.
        // Scanning the methods on each call is expensive so figure out what the ctor
        // and setOutcome methods are on first use then cache them.
        return outcomeFactoryMap.computeIfAbsent(eventAction.getClass(), clazz -> {

            return Arrays.stream(eventAction.getClass().getMethods())
                    .filter(method -> method.getName().equals("setOutcome"))
                    .findAny()
                    .flatMap(method -> {
                        Class<?> outcomeClass = method.getParameterTypes()[0];

                        Constructor<?> constructor;
                        try {
                            constructor = outcomeClass.getDeclaredConstructor();
                        } catch (NoSuchMethodException e) {
                            LOGGER.warn("No noargs constructor found for " + outcomeClass.getName(), e);
                            return Optional.empty();
                        }

                        final Function<EventAction, BaseOutcome> func = eventAction2 -> {
                            try {
                                final BaseOutcome outcome = (BaseOutcome) constructor.newInstance();
                                method.invoke(eventAction, outcomeClass.cast(outcome));
                                return outcome;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        };
                        LOGGER.debug("Caching function for {}", eventAction.getClass().getName());
                        return Optional.of(func);
                    });
        })
        .flatMap(func ->
                Optional.of(func.apply(eventAction)));
    }
}
