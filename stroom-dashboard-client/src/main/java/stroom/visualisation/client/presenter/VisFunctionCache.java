/*
 * Copyright 2017 Crown Copyright
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

package stroom.visualisation.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dashboard.client.vis.ClearFunctionCacheEvent;
import stroom.dashboard.client.vis.HandlerRegistry;
import stroom.query.api.v1.DocRef;
import stroom.security.client.event.LogoutEvent;

import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class VisFunctionCache {
    private final EventBus eventBus;
    private final HandlerRegistry handlerRegistry = new HandlerRegistry();

    private final Map<DocRef, VisFunction> map = new HashMap<>();
    private int functionNo;

    @Inject
    public VisFunctionCache(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void bind() {
        // Listen for logout events.
        handlerRegistry.registerHandler(eventBus.addHandler(LogoutEvent.getType(), event -> map.clear()));
        handlerRegistry.registerHandler(
                eventBus.addHandler(ClearFunctionCacheEvent.getType(), event -> {
                    if (event.getVisualisation() != null) {
                        map.remove(event.getVisualisation());
                    } else {
                        map.clear();
                    }
                }));
    }

    public void unbind() {
        handlerRegistry.unbind();
    }

    public VisFunction get(final DocRef visualisation) {
        return map.get(visualisation);
    }

    public VisFunction create(final DocRef visualisation) {
        final VisFunction function = new VisFunction(functionNo++);
        map.put(visualisation, function);
        return function;
    }
}
