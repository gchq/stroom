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

import event.logging.EventAction;

/**
 * Interface for simple classes that modify {@link EventAction} instances to improve quality of Autologger logging.
 *
 * @param <A> the type of event action that this EventActionDecorator works for
 */

public interface EventActionDecorator<A extends EventAction> {

    /**
     * Decorate this event action
     *
     * @param eventAction the basic (automatically assigned) event action
     * @return a decorated version of the supplied eventAction
     */
    A decorate(final A eventAction);

}
