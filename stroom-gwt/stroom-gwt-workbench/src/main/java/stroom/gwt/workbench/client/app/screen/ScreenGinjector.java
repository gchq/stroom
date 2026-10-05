/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.client.app.screen;

import stroom.dispatch.client.RestFactory;
import stroom.dispatch.client.RestModule;

import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;
import com.google.web.bindery.event.shared.EventBus;

/// The minimal GIN injector a screen story needs: Stroom's real [RestFactory] (whose
/// implementation is package private, so can only be created by GIN) and the event bus it fires
/// alerts and task events on. Everything else a screen needs is created with `new` by the story.
@GinModules({RestModule.class, ScreenGinModule.class})
public interface ScreenGinjector extends Ginjector {

    /// @return The event bus, one per injector.
    EventBus getEventBus();

    /// @return Stroom's REST factory, one per injector.
    RestFactory getRestFactory();
}
