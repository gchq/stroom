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

import stroom.analytics.client.presenter.AnalyticDataShardsPresenter;
import stroom.analytics.client.presenter.AnalyticDataShardsPresenter.AnalyticDataShardsView;
import stroom.analytics.client.presenter.AnalyticRulePresenter;
import stroom.analytics.client.view.AnalyticDataShardsViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `AnalyticsModule`
/// (`stroom/analytics/client/gin/AnalyticsModule.java`), without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class AnalyticsScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(AnalyticRulePresenter.class);
        bindPresenterWidget(AnalyticDataShardsPresenter.class,
                AnalyticDataShardsView.class,
                AnalyticDataShardsViewImpl.class);
    }
}

