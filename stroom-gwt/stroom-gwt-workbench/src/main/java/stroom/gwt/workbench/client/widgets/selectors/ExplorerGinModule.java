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


package stroom.gwt.workbench.client.widgets.selectors;

import stroom.dispatch.client.RestFactory;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Provides;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// The bindings of [ExplorerGinjector]: the explorer popup's view as Stroom binds it, and the
/// services of the harness that the controller is being created for (see
/// [SelectorWidgets#documentChooser]), so the popup shares them with the rest of the story.
public class ExplorerGinModule extends AbstractGinModule {

    // The harness the controller being created belongs to, set only while it is created
    private static ScreenHarness current;

    /// Sets the harness whose services new controllers get, or clears it.
    ///
    /// @param harness The harness, or null once the controller has been created.
    static void setCurrent(final ScreenHarness harness) {
        current = harness;
    }

    @Override
    protected void configure() {
    }

    /// @return The current harness's event bus.
    @Provides
    EventBus provideEventBus() {
        return harness().getEventBus();
    }

    /// @return The current harness's REST factory.
    @Provides
    RestFactory provideRestFactory() {
        return harness().getRestFactory();
    }

    /// @return The current harness's UI config cache.
    @Provides
    UiConfigCache provideUiConfigCache() {
        return harness().getUiConfigCache();
    }

    @Provides
    QuickFilterContextHandlerFactory provideQuickFilterContextHandlerFactory() {
        return SelectorWidgets.quickFilterContextHandlerFactory(harness());
    }

    private static ScreenHarness harness() {
        return Objects.requireNonNull(current, "Create controllers with SelectorWidgets");
    }
}
