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
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.dropdowntree.client.view.ExplorerPopupView;
import stroom.widget.dropdowntree.client.view.ExplorerPopupViewImpl;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Provides;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;

/// The bindings of [ExplorerGinjector]: the explorer popup's view as Stroom binds it, and the
/// services of the harness that the presenter is being created for (see
/// [SelectorWidgets#explorerPopup]), so the popup shares them with the rest of the story.
public class ExplorerGinModule extends AbstractGinModule {

    // The harness the presenter being created belongs to, set only while it is created
    private static ScreenHarness current;

    /// Sets the harness whose services new presenters get, or clears it.
    ///
    /// @param harness The harness, or null once the presenter has been created.
    static void setCurrent(final ScreenHarness harness) {
        current = harness;
    }

    @Override
    protected void configure() {
        bind(ExplorerPopupView.class).to(ExplorerPopupViewImpl.class);
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

    private static ScreenHarness harness() {
        return Objects.requireNonNull(current, "Create presenters with SelectorWidgets");
    }
}
