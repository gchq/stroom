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

package stroom.gwt.workbench.client.app.gin.dashboard;

import stroom.dashboard.client.main.ComponentRegistry;
import stroom.visualisation.client.presenter.VisFunctionCache;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Singleton;

/// The presenter and view bindings of Stroom's `DashboardModule`
/// (`stroom/dashboard/client/gin/DashboardModule.java`), without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
///
/// Unlike the other mirrors, it keeps the dashboard's singletons: the `ComponentRegistry` (which the
/// component plugins fill, and every dashboard's `Components` reads) and the `VisFunctionCache`
/// (bound by Stroom's `VisModule` and `EmbeddedQueryModule`).
public class DashboardScreenModule extends AbstractGinModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(ComponentRegistry.class).in(Singleton.class);
        bind(VisFunctionCache.class).in(Singleton.class);

    }
}

