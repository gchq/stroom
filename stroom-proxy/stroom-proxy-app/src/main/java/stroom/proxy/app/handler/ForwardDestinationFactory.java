/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.app.handler;


/**
 * Builds the {@link Destination} a forward destination's configuration describes. The destination
 * is the delivery mechanism alone; retry, back-off and give-up are the forward stage's.
 */
public interface ForwardDestinationFactory<T extends ForwarderConfig> {

    Destination create(T config);

    Class<T> getConfigClass();

}
