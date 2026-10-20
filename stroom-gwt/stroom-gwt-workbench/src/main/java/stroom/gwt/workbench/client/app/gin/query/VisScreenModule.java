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

package stroom.gwt.workbench.client.app.gin.query;

import stroom.dashboard.client.vis.BasicVisSettingsPresenter;
import stroom.dashboard.client.vis.BasicVisSettingsPresenter.BasicVisSettingsView;
import stroom.dashboard.client.vis.BasicVisSettingsViewImpl;
import stroom.dashboard.client.vis.VisPresenter;
import stroom.dashboard.client.vis.VisPresenter.VisView;
import stroom.dashboard.client.vis.VisViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `VisModule`
/// (`stroom/dashboard/client/vis/gin/VisModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class VisScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(VisPresenter.class,
                VisView.class,
                VisViewImpl.class);
        bindPresenterWidget(BasicVisSettingsPresenter.class,
                BasicVisSettingsView.class,
                BasicVisSettingsViewImpl.class);
    }
}

