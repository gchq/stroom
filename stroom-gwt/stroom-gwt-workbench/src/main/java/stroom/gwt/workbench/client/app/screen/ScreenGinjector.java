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
import stroom.editor.client.presenter.CurrentPreferences;
import stroom.preferences.client.DateTimeFormatter;
import stroom.preferences.client.UserPreferencesManager;
import stroom.security.client.api.ClientSecurityContext;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.inject.client.GinModules;
import com.google.gwt.inject.client.Ginjector;
import com.google.web.bindery.event.shared.EventBus;

/// The GIN injector of a screen story's harness, one per harness. It provides the Stroom
/// services that screens commonly need, created as Stroom's GIN modules would but with the
/// story's fakes bound in place of the real app's:
///
/// * [EventBus] → [StoryEventBus];
/// * [RestFactory] → Stroom's real `RestFactoryImpl`, sending its requests with
///   [org.fusesource.restygwt.client.Dispatcher] → [StoryDispatcher] (in place of Stroom's network
///   `RestDispatcher`), which passes them to the harness's fixtures;
/// * [ClientSecurityContext] → [StorySecurityContext] (in place of Stroom's `CurrentUser`);
/// * [LocationManager] → [StoryLocationManager] (downloads are recorded, not navigated to);
/// * `GlobalKeyHandler` → [StoryGlobalKeyHandler] (no app keyboard shortcuts).
///
/// Every getter returns a singleton of the injector, so e.g. a story's presenter and the harness
/// share one [UiConfigCache]. Screens (presenters and views) are not added here: a story creates
/// a simple one with `new`, passing in what it gets from here, or gets it from
/// `stroom.gwt.workbench.client.app.gin.AppScreenGinjector`, which extends this with Stroom's
/// presenter bindings (see `ScreenHarness.Builder.injector`).
@GinModules(ScreenGinModule.class)
public interface ScreenGinjector extends Ginjector {

    /// @return The event bus.
    EventBus getEventBus();

    /// @return The event bus, as the harness's type.
    StoryEventBus getStoryEventBus();

    /// @return The REST factory, whose requests are answered by the story's fixtures.
    RestFactory getRestFactory();

    /// @return The dispatcher the REST factory sends its requests with, for the harness to point
    /// at its fixtures.
    StoryDispatcher getStoryDispatcher();

    /// @return The security context.
    ClientSecurityContext getClientSecurityContext();

    /// @return The security context, as the harness's type, to configure.
    StorySecurityContext getSecurityContext();

    /// @return The location manager, as the harness's type.
    StoryLocationManager getLocationManager();

    /// @return Stroom's cache of the UI config, which fetches it with the REST factory.
    UiConfigCache getUiConfigCache();

    /// @return Stroom's user preferences manager, which fetches them with the REST factory.
    UserPreferencesManager getUserPreferencesManager();

    /// @return The current preferences (theme, editor settings).
    CurrentPreferences getCurrentPreferences();

    /// @return Stroom's date/time formatter, using the user preferences.
    DateTimeFormatter getDateTimeFormatter();
}
