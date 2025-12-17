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

package stroom.event.logging.api;

import stroom.entity.shared.ExpressionCriteria;

import event.logging.BaseObject;
import event.logging.Criteria;
import event.logging.Data;
import event.logging.EventLoggingService;
import event.logging.MultiObject;
import event.logging.UpdateEventAction;

import java.util.List;
import java.util.function.Supplier;

public interface StroomEventLoggingService extends EventLoggingService {

    String UNKNOWN_OBJECT_DESCRIPTION = "Unknown";

//    Event createSkeletonEvent(final String typeId,
//                              final String description);
//
//    Event createSkeletonEvent(final String typeId,
//                              final String description,
//                              final Consumer<Builder<Void>> eventDetailBuilderConsumer);

//    void log(final String typeId,
//             final String description,
//             final Consumer<Builder<Void>> eventDetailBuilderConsumer);

//    default <T_EVENT_ACTION extends EventAction> void loggedAction(
//            final String eventTypeId,
//            final String description,
//            final T_EVENT_ACTION eventAction,
//            final Runnable loggedAction) {
//
//        final Function<T_EVENT_ACTION, LoggedResult<Void, T_EVENT_ACTION>> loggedResultFunction = event -> {
//            loggedAction.run();
//            return LoggedResult.of(null, event);
//        };
//
//        loggedResult(
//                eventTypeId,
//                description,
//                eventAction,
//                loggedResultFunction,
//                null);
//    }
//
//    default <T_EVENT_ACTION extends EventAction> void loggedAction(
//            final String eventTypeId,
//            final String description,
//            final T_EVENT_ACTION eventAction,
//            final UnaryOperator<T_EVENT_ACTION> loggedAction,
//            final BiFunction<T_EVENT_ACTION, Throwable, T_EVENT_ACTION> exceptionHandler) {
//
//        final Function<T_EVENT_ACTION, LoggedResult<Void, T_EVENT_ACTION>> loggedResultFunction = eventAction2 ->
//                LoggedResult.of(null, loggedAction.apply(eventAction2));
//
//        loggedResult(
//                eventTypeId,
//                description,
//                eventAction,
//                loggedResultFunction,
//                exceptionHandler);
//    }
//
//    /**
//     * See also {@link StroomEventLoggingService#loggedResult(String, String, EventAction, Function, BiFunction)}
//     * Use this form when you do not need to modify the event based on the result of the work.
//     * If an exception occurs in {@code loggedWork} then an unsuccessful outcome will be added to the
//     * {@link EventAction} before it is logged.
//     */
//    default <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
//            final String eventTypeId,
//            final String description,
//            final T_EVENT_ACTION eventAction,
//            final Supplier<T_RESULT> loggedWork) {
//
//        final Function<T_EVENT_ACTION, LoggedResult<T_RESULT, T_EVENT_ACTION>> loggedResultFunction = event ->
//                LoggedResult.of(loggedWork.get(), event);
//
//        return loggedResult(
//                eventTypeId,
//                description,
//                eventAction,
//                loggedResultFunction,
//                null);
//    }
//
//    /**
//     * Performs {@code loggedWork} and logs an event using the supplied {@link EventAction}.
//     * An event is logged if the work is successful or if an exception occurs.
//     * Use this form when you want to modify the event based on the result of the work, e.g. recording the
//     * before and after of an update, or any exception thrown performing the work.
//     * @param eventTypeId The value to set on Event/EventDetail/TypeId. See
//     *                    {@link event.logging.EventDetail#setTypeId(String)} for details.
//     * @param description A human readable description of the event being logged. Can include IDs/values specific
//     *                    to the event, e.g. "Creating user account jbloggs". See also
//     *                    {@link event.logging.EventDetail#setDescription(String)}
//     * @param eventAction The skeleton {@link EventAction} that will be used to create the event unless
//     *                    {@code loggedWork} of {@code exceptionHandler} provide an alternative.
//     * @param loggedWork A function to perform the work that is being logged and to return the {@link EventAction}
//     *                   and the result of the work. This allows a new {@link EventAction} to be returned
//     *                   based on the result of the work. The skeleton {@link EventAction} is passed in
//     *                   to allow it to be copied. The result of the work must be returned within a
//     {@link LoggedResult}
//     *                   along with the desired {@link EventAction}.
//     * @param exceptionHandler A function to allow you to provide a different {@link EventAction} based on
//     *                         the exception. The skeleton {@link EventAction} is passed in to allow it to be
//     *                         copied.<br/>
//     *                         If null then an outcome will be set on the skeleton event action and
//     *                         the exception message will be added to the outcome
//     *                         description. The outcome success will be set to false.<br/>
//     *                         In either case, an event will be logged and the original exception rethrown for
//     *                         the caller to handle. Any exceptions in the handler will be ignored and the original
//     *                         exception rethrown.
//     */
//    <T_RESULT, T_EVENT_ACTION extends EventAction> T_RESULT loggedResult(
//            final String eventTypeId,
//            final String description,
//            final T_EVENT_ACTION eventAction,
//            final Function<T_EVENT_ACTION, LoggedResult<T_RESULT, T_EVENT_ACTION>> loggedWork,
//            final BiFunction<T_EVENT_ACTION, Throwable, T_EVENT_ACTION> exceptionHandler);

    /**
     * Convert the supplied POJO into a {@link BaseObject} for logging
     * If an {@link ObjectInfoProvider} implementation is registered for this class, then it is used to perform the
     * actual conversion.
     * Otherwise, Java introspection is used to derive {@link event.logging.Data} items from Java bean properties.
     *
     * @param object           POJO
     * @param useInfoProviders Set to false to not use {@link ObjectInfoProvider} classes.
     * @return BaseObject
     */
    BaseObject convert(final Object object, final boolean useInfoProviders);

    default BaseObject convert(final Object object) {
        return convert(object, true);
    }

    /**
     * Convert the supplied POJO into a {@link BaseObject} for logging
     * If an {@link ObjectInfoProvider} implementation is registered for this class, then it is used to perform the
     * actual conversion.
     * Otherwise, Java introspection is used to derive {@link event.logging.Data} items from Java bean properties.
     *
     * @param objectSupplier   Supplier of the POJO. get() will be called as the processing user
     * @param useInfoProviders Set to false to not use {@link ObjectInfoProvider} classes.
     * @return BaseObject
     */
    BaseObject convert(final Supplier<?> objectSupplier, final boolean useInfoProviders);

    default BaseObject convert(final Supplier<?> objectSupplier) {
        return convert(objectSupplier, true);
    }

    default MultiObject convertToMulti(final Supplier<?> objectSupplier) {
        return MultiObject.builder()
                .withObjects(convert(objectSupplier))
                .build();
    }

    default MultiObject convertToMulti(final Object object) {
        return MultiObject.builder()
                .withObjects(convert(object))
                .build();
    }

    default <T> UpdateEventAction buildUpdateEventAction(final Supplier<T> beforeSupplier,
                                                         final Supplier<T> afterSupplier) {
        return UpdateEventAction.builder()
                .withBefore(convertToMulti(beforeSupplier))
                .withAfter(convertToMulti(afterSupplier))
                .build();
    }

    default <T> UpdateEventAction buildUpdateEventAction(final Object before,
                                                         final Object after) {
        return UpdateEventAction.builder()
                .withBefore(convertToMulti(before))
                .withAfter(convertToMulti(after))
                .build();
    }

    Criteria convertExpressionCriteria(final String type,
                                       final ExpressionCriteria expressionCriteria);

    /**
     * Provide a textual summary of the supplied POJO as a string.
     *
     * @param object POJO to describe
     * @return description
     */
    String describe(final Object object);


    /**
     * Create {@link Data} items from properties of the supplied POJO
     *
     * @param obj POJO from which to extract properties
     * @return List of {@link Data} items representing properties of the supplied POJO
     */
    List<Data> getDataItems(java.lang.Object obj);
}
