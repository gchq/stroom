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

package stroom.event.logging.api;

import event.logging.Event;
import event.logging.EventAction;
import event.logging.EventDetail.Builder;
import event.logging.EventLoggingService;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface StroomEventLoggingService extends EventLoggingService {

    Event createSkeletonEvent(final String typeId,
                              final String description);

    Event createSkeletonEvent(final String typeId,
                              final String description,
                              final Consumer<Builder<Void>> eventDetailBuilderConsumer);

    void log(final String typeId,
             final String description,
             final Consumer<Builder<Void>> eventDetailBuilderConsumer);

    default <T_EVENT_ACTION extends EventAction> void loggedAction(
            final String eventTypeId,
            final String description,
            final T_EVENT_ACTION eventAction,
            final Runnable loggedAction) {

        final Function<T_EVENT_ACTION, LoggedResult<Void, T_EVENT_ACTION>> loggedResultFunction = event -> {
            loggedAction.run();
            return LoggedResult.of(null, event);
        };

        loggedResult(
                eventTypeId,
                description,
                eventAction,
                loggedResultFunction,
                null);
    }

    /**
     * See also {@link StroomEventLoggingService#loggedResult(String, String, EventAction, Function, BiFunction)}
     * Use this form when you do not need to modify the event based on the result of the work.
     * If an exception occurs in {@code loggedWork} then an unsuccessful outcome will be added to the
     * {@link EventAction} before it is logged.
     */
    default <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
            final String eventTypeId,
            final String description,
            final T_EVENT_ACTION eventAction,
            final Supplier<T_RESULT> loggedWork) {

        final Function<T_EVENT_ACTION, LoggedResult<T_RESULT, T_EVENT_ACTION>> loggedResultFunction = event ->
                LoggedResult.of(loggedWork.get(), event);

        return loggedResult(
                eventTypeId,
                description,
                eventAction,
                loggedResultFunction,
                null);
    }

    /**
     * Performs {@code loggedWork} and logs an event using the supplied {@link EventAction}.
     * An event is logged if the work is successful or if an exception occurs.
     * Use this form when you want to modify the event based on the result of the work, e.g. recording the
     * before and after of an update, or any exception thrown performing the work.
     * @param eventTypeId The value to set on Event/EventDetail/TypeId. See
     *                    {@link event.logging.EventDetail#setTypeId(String)} for details.
     * @param description A human readable description of the event being logged. Can include IDs/values specific
     *                    to the event, e.g. "Creating user account jbloggs". See also
     *                    {@link event.logging.EventDetail#setDescription(String)}
     * @param eventAction The skeleton {@link EventAction} that will be used to create the event unless
     *                    {@code loggedWork} of {@code exceptionHandler} provide an alternative.
     * @param loggedWork A function to perform the work that is being logged and to return the {@link EventAction}
     *                   and the result of the work. This allows a new {@link EventAction} to be returned
     *                   based on the result of the work. The skeleton {@link EventAction} is passed in
     *                   to allow it to be copied. The result of the work must be returned within a {@link LoggedResult}
     *                   along with the desired {@link EventAction}.
     * @param exceptionHandler A function to allow you to provide a different {@link EventAction} based on
     *                         the exception. The skeleton {@link EventAction} is passed in to allow it to be
     *                         copied.<br/>
     *                         If null then an outcome will be set on the skeleton event action and
     *                         the exception message will be added to the outcome
     *                         description. The outcome success will be set to false.<br/>
     *                         In either case, an event will be logged and the original exception rethrown for
     *                         the caller to handle. Any exceptions in the handler will be ignored and the original
     *                         exception rethrown.
     */
    <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
            final String eventTypeId,
            final String description,
            final T_EVENT_ACTION eventAction,
            final Function<T_EVENT_ACTION, LoggedResult<T_RESULT, T_EVENT_ACTION>> loggedWork,
            final BiFunction<T_EVENT_ACTION, Throwable, T_EVENT_ACTION> exceptionHandler);

}
