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

package stroom.script.client;

import stroom.dashboard.client.vis.ClearScriptCacheEvent;
import stroom.dashboard.client.vis.HandlerRegistry;
import stroom.docref.DocRef;
import stroom.security.client.api.event.LogoutEvent;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;
import javax.inject.Singleton;

@Singleton
public class ScriptCache {

    private final EventBus eventBus;
    private final HandlerRegistry handlerRegistry = new HandlerRegistry();

    private final Set<DocRef> loadedScripts = new HashSet<>();

    @Inject
    public ScriptCache(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    public void bind() {
        // Listen for logout events.
        handlerRegistry.registerHandler(eventBus.addHandler(LogoutEvent.getType(), event -> loadedScripts.clear()));
        handlerRegistry.registerHandler(
                eventBus.addHandler(ClearScriptCacheEvent.getType(), event -> {
                    if (event.getScript() != null) {
                        loadedScripts.remove(event.getScript());
                    } else {
                        loadedScripts.clear();
                    }
                }));
    }

    public void unbind() {
        handlerRegistry.unbind();
    }

    public Set<DocRef> getLoadedScripts() {
        return loadedScripts;
    }
}
