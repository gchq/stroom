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

import stroom.core.client.LocationManager;
import stroom.dispatch.client.RestFactory;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.util.client.GlobalKeyHandler;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import org.fusesource.restygwt.client.Dispatcher;

/// Binds the story fakes of [ScreenGinjector] in place of the real app's services.
public class ScreenGinModule extends AbstractGinModule {

    /// Binds the fakes.
    @Override
    protected void configure() {
        bind(StoryEventBus.class).in(Singleton.class);
        bind(EventBus.class).to(StoryEventBus.class);
        bind(StoryDispatcher.class).in(Singleton.class);
        bind(Dispatcher.class).to(StoryDispatcher.class);
        bind(StorySecurityContext.class).in(Singleton.class);
        bind(ClientSecurityContext.class).to(StorySecurityContext.class);
        bind(StoryLocationManager.class).in(Singleton.class);
        bind(LocationManager.class).to(StoryLocationManager.class);
        bind(GlobalKeyHandler.class).to(StoryGlobalKeyHandler.class).in(Singleton.class);
    }

    /// Creates Stroom's real REST factory, as Stroom's `RestModule` would but with this injector's
    /// dispatcher in place of Stroom's network one (see [StroomRestFactory]).
    ///
    /// @param eventBus   This injector's event bus.
    /// @param dispatcher This injector's dispatcher, which passes requests to the harness's
    ///                   fixtures.
    /// @return The REST factory.
    @Provides
    @Singleton
    public RestFactory provideRestFactory(final StoryEventBus eventBus, final StoryDispatcher dispatcher) {
        return StroomRestFactory.create(eventBus, dispatcher);
    }
}
