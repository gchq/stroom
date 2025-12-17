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

package stroom.dashboard.client.vis;

import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.List;

public class HandlerRegistry {

    private List<HandlerRegistration> handlerRegistrations;

    public void unbind() {
        if (handlerRegistrations != null) {
            for (final HandlerRegistration handlerRegistration : handlerRegistrations) {
                handlerRegistration.removeHandler();
            }
        }
        handlerRegistrations = null;
    }

    public void registerHandler(final HandlerRegistration handlerRegistration) {
        if (handlerRegistrations == null) {
            handlerRegistrations = new ArrayList<>();
        }
        handlerRegistrations.add(handlerRegistration);
    }
}
