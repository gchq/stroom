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

package stroom.event.logging.rs.api;

import stroom.event.logging.api.EventActionDecorator;

import event.logging.EventAction;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

/**
 * Methods may be annotated with this in order to indicate that their calls should be logged automatically by the
 * runtime/hosting environment.  Similarly, a class itself may be annotated with this in order to indicate that
 * all method calls should be logged.
 * <p>
 * Generated log is expected to be event-logging XML format and where possible sensible defaults selected for
 * the fields within the logged event, based on the method name, the parameters provided and the context.
 * In certain cases, these defaults may not be suitable, where they may be modified via attributes to the annotation.
 * <p>
 * This annotation is supported for resources within Java RS containers via RestResourceAutoLogger.
 */
//TODO consider whether this should be @NameBinding
// Currently not to allow all calls to be logged via runtime config, if required and
// to allow only the resource class itself to be annotated rather than every method.
@Inherited
@Target({TYPE, METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoLogged {

    String ALLOCATE_AUTOMATICALLY = "";

    /**
     * The rough nature of the operation.
     * <p>
     * The default is OperationType.ALLOCATE_AUTOMATICALLY which requires the
     * implementation to select based on method name and other information e.g. HTTP Method.
     * <p>
     * This attribute is not normally set for class level annotations.
     *
     * @return the operation type.
     */
    OperationType value() default OperationType.ALLOCATE_AUTOMATICALLY;

    /**
     * A literal that uniquely identifies a particular operation.
     * The default is a combination of the name of the class and the method.
     * <p>
     * This attribute is not normally set for class level annotations.
     *
     * @return the type id associated with the annotated method
     */
    String typeId() default ALLOCATE_AUTOMATICALLY;

    /**
     * A literal that is used by the logging implementation to create a human readable description of the logged event.
     * Default values are based on the operation type
     * <p>
     * Example verbs: "Generating", "Resetting"
     * <p>
     * This attribute is not normally set for class level annotations.
     *
     * @return the human readable verb associated with the annotated method
     */
    String verb() default ALLOCATE_AUTOMATICALLY;

    /**
     * An optional {@link EventActionDecorator} that should be used to decorate the automatically
     * generated {@link EventAction}
     * The default value is {@link EventActionDecorator} itself (the baseclass) which is taken to mean "no decorator".
     *
     * @return the decorated {@link EventAction}
     */
    Class<? extends EventActionDecorator> decorator() default EventActionDecorator.class;

    /**
     * Enumeration of all recognised event types.
     * <p>
     * Most values relate to types of event defeined in event-logging XML schema (refer to event-logging schema).
     * The following values are additional/special:
     * UNLOGGED - The system should not log this class/method.
     * ALLOCATE_AUTOMATICALLY - the system should determine event type based on resource method name and HTTP method.
     */
    enum OperationType {
        /**
         * Special type - don't log at all
         */
        UNLOGGED,

        /**
         * Special type - logging will be done via some other means, not the auto logger
         */
        MANUALLY_LOGGED,

        /**
         * Special type - system should determine event type based on resource method name and HTTP method
         */
        ALLOCATE_AUTOMATICALLY,

        //Standard event types follow
        CREATE,
        UPDATE,
        DELETE,
        VIEW,
        COPY,
        SEARCH,
        PROCESS,
        IMPORT,
        EXPORT,
        UNKNOWN
    }
}
