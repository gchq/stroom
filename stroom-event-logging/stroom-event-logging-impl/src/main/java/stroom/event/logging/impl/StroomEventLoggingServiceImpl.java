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
import event.logging.CopyMoveOutcome;
import event.logging.Device;
import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventDetail;
import event.logging.EventDetail.Builder;
import event.logging.EventSource;
import event.logging.EventTime;
import event.logging.File;
import event.logging.HasOutcome;
import event.logging.MoveEventAction;
import event.logging.MultiObject;
import event.logging.SystemDetail;
import event.logging.User;
import event.logging.impl.DefaultEventLoggingService;
import event.logging.util.DeviceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

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
        final EventDetail.Builder<Void> eventDetailBuilder = EventDetail.builder()
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

        return securityContext.insecureResult(() -> {
            final T_RESULT result;
            if (loggedWork != null) {
                final Event event = createSkeletonEvent(
                        eventTypeId,
                        description,
                        eventDetailBuilder -> eventDetailBuilder
                                .withEventAction(eventAction));

                try {
                    // Allow the caller to provide a new EventAction bases on the result of the work
                    // e.g. if they are updating a record, they can capture the before state
                    final LoggedResult<T_RESULT, T_EVENT_ACTION> loggedResult = loggedWork.apply(eventAction);
                    // Set the new EventAction onto the existing event
                    event.getEventDetail().setEventAction(loggedResult.getEventAction());
                    log(event);
                    result = loggedResult.getResult();
                } catch (Throwable e) {
                    if (exceptionHandler != null) {
                        // Allow caller to provide a new EventAction bases on the exception
                        T_EVENT_ACTION newEventAction = exceptionHandler.apply(eventAction, e);
                        event.getEventDetail().setEventAction(newEventAction);
                    } else {
                        // No handler so see if we can add an outcome
                        if (eventAction instanceof HasOutcome) {

                            final HasOutcome hasOutcome = (HasOutcome) eventAction;
                            final BaseOutcome baseOutcome = hasOutcome.getOutcome();
                            if (baseOutcome != null) {
                                baseOutcome.setSuccess(false);
                                baseOutcome.setDescription(e.getMessage() != null
                                        ? e.getMessage()
                                        : e.getClass().getName());
                            } else {
                                // TODO @AT Need to find a way of initialising the outcome when we don't know what
                                // type it is.
                                LOGGER.warn("Unable set outcome as baseOutcome is null");
                            }
                        }
                    }
                    log(event);
                    throw e;
                }
            } else {
                result = null;
            }
            return result;
        });
    }

//    @Override
//    public <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
//            final String eventTypeId,
//            final String description,
//            final T_EVENT_ACTION eventAction,
//            final Function<T_EVENT_ACTION, T_RESULT> loggedWork) {
//        return loggedResult(eventTypeId, description, eventAction, loggedWork, null);
//    }
//
//    @Override
//    public <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
//            final String eventTypeId,
//            final String description,
//            final T_EVENT_ACTION eventAction,
//            final Function<T_EVENT_ACTION, T_RESULT> loggedWork,
//            final BiConsumer<Throwable, T_EVENT_ACTION> exceptionHandler) {
//
//        return securityContext.insecureResult(() -> {
//            final T_RESULT result;
//            if (loggedWork != null) {
//                final Event event = createSkeletonEvent(
//                        eventTypeId,
//                        description,
//                        eventDetailBuilder -> eventDetailBuilder
//                                .withEventAction(eventAction));
//
//                try {
//                    // Allow the caller to mutate the eventAction based on the work that they do,
//                    // e.g. if they are updating a record, they can capture the before state
//                    result = loggedWork.apply(eventAction);
//                    log(event);
//                } catch (Throwable e) {
//                    if (exceptionHandler != null) {
//                        // Allow caller to mutate the action based on the exception
//                        exceptionHandler.accept(e, eventAction);
//                    } else {
//                        // No handler so see if we can add an outcome
//                        if (eventAction instanceof HasOutcome) {
//
//                            final HasOutcome hasOutcome = (HasOutcome) eventAction;
//                            final BaseOutcome baseOutcome = hasOutcome.getOutcome();
//                            if (baseOutcome != null) {
//                                baseOutcome.setSuccess(false);
//                                baseOutcome.setDescription(e.getMessage() != null
//                                        ? e.getMessage()
//                                        : e.getClass().getName());
//                            } else {
//                                // TODO @AT Need to find a way of initialising the outcome when we don't know what
//                                // type it is.
//                                LOGGER.warn("Unable set outcome as baseOutcome is null");
//                            }
//                        }
//                    }
//                    log(event);
//                    throw e;
//                }
//            } else {
//                result = null;
//            }
//            return result;
//        });
//    }

    private void loggedResultTest() {

        final long id = 123;
        final String newFilePath = "/tmp/xxx.txt";

        final MoveEventAction skeletonAction = MoveEventAction.builder()
                .withDestination(MultiObject.builder()
                        .addFile(File.builder()
                                .withId(Long.toString(id))
                                .withPath(newFilePath)
                                .build())
                        .build())
                .withOutcome(CopyMoveOutcome.builder()
                        .withSuccess(true)
                        .build())
                .build();

        final Function<MoveEventAction, Integer> loggedWork = moveEventAction -> {
            // Find out where the old file was
            final String oldPath = "/tmp/yyy.txt";

            // Add in details of from path
            moveEventAction.setSource(MultiObject.builder()
                    .addFile(File.builder()
                            .withId(Long.toString(id))
                            .withPath(oldPath)
                            .build())
                    .build());

            // Now do the actual work and return its result
            return 0;
        };

        final BiConsumer<Throwable, MoveEventAction> exceptionHandler = (e, moveEventAction) -> {
            // Find out where the old file was
            final String oldPath = "/tmp/yyy.txt";

            // Add in details of from path
            moveEventAction.setSource(MultiObject.builder()
                    .addFile(File.builder()
                            .withId(Long.toString(id))
                            .withPath(oldPath)
                            .build())
                    .build());

            moveEventAction.setOutcome(CopyMoveOutcome.builder()
                    .withSuccess(false)
                    .withDescription("Bad things happened: " + e.getMessage())
                    .build());
        };

//        final int result = loggedResult(
//                "moveFile",
//                "move file " + id + " to " + newFilePath,
//                skeletonAction,
//                loggedWork,
//                exceptionHandler);
    }
}
