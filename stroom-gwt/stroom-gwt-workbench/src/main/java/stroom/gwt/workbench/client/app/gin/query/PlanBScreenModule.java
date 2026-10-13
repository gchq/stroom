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

import stroom.planb.client.presenter.HistogramSettingsPresenter;
import stroom.planb.client.presenter.HistogramSettingsPresenter.HistogramSettingsView;
import stroom.planb.client.presenter.MetricSettingsPresenter;
import stroom.planb.client.presenter.MetricSettingsPresenter.MetricSettingsView;
import stroom.planb.client.presenter.PlanBPresenter;
import stroom.planb.client.presenter.PlanBSettingsPresenter;
import stroom.planb.client.presenter.PlanBSettingsPresenter.PlanBSettingsView;
import stroom.planb.client.presenter.RangeStateSettingsPresenter;
import stroom.planb.client.presenter.RangeStateSettingsPresenter.RangeStateSettingsView;
import stroom.planb.client.presenter.SessionSettingsPresenter;
import stroom.planb.client.presenter.SessionSettingsPresenter.SessionSettingsView;
import stroom.planb.client.presenter.StateSettingsPresenter;
import stroom.planb.client.presenter.StateSettingsPresenter.StateSettingsView;
import stroom.planb.client.presenter.TemporalRangeStateSettingsPresenter;
import stroom.planb.client.presenter.TemporalRangeStateSettingsPresenter.TemporalRangeStateSettingsView;
import stroom.planb.client.presenter.TemporalStateSettingsPresenter;
import stroom.planb.client.presenter.TemporalStateSettingsPresenter.TemporalStateSettingsView;
import stroom.planb.client.presenter.TraceSettingsPresenter;
import stroom.planb.client.presenter.TraceSettingsPresenter.TraceSettingsView;
import stroom.planb.client.view.HistogramSettingsViewImpl;
import stroom.planb.client.view.MetricSettingsViewImpl;
import stroom.planb.client.view.PlanBSettingsViewImpl;
import stroom.planb.client.view.RangeStateSettingsViewImpl;
import stroom.planb.client.view.SessionSettingsViewImpl;
import stroom.planb.client.view.StateSettingsViewImpl;
import stroom.planb.client.view.TemporalRangeStateSettingsViewImpl;
import stroom.planb.client.view.TemporalStateSettingsViewImpl;
import stroom.planb.client.view.TraceSettingsViewImpl;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `PlanBModule`
/// (`stroom/planb/client/gin/PlanBModule.java`), without its plugins and app services.
/// Presenters that Stroom binds with a GWTP proxy are bound as presenter widgets with a null
/// proxy: a story shows one by registering it as the handler of its event, as the proxy would.
public class PlanBScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bind(PlanBPresenter.class);
        bindPresenterWidget(PlanBSettingsPresenter.class,
                PlanBSettingsView.class,
                PlanBSettingsViewImpl.class);
        bindPresenterWidget(StateSettingsPresenter.class,
                StateSettingsView.class,
                StateSettingsViewImpl.class);
        bindPresenterWidget(TemporalStateSettingsPresenter.class,
                TemporalStateSettingsView.class,
                TemporalStateSettingsViewImpl.class);
        bindPresenterWidget(RangeStateSettingsPresenter.class,
                RangeStateSettingsView.class,
                RangeStateSettingsViewImpl.class);
        bindPresenterWidget(TemporalRangeStateSettingsPresenter.class,
                TemporalRangeStateSettingsView.class,
                TemporalRangeStateSettingsViewImpl.class);
        bindPresenterWidget(SessionSettingsPresenter.class,
                SessionSettingsView.class,
                SessionSettingsViewImpl.class);
        bindPresenterWidget(HistogramSettingsPresenter.class,
                HistogramSettingsView.class,
                HistogramSettingsViewImpl.class);
        bindPresenterWidget(MetricSettingsPresenter.class,
                MetricSettingsView.class,
                MetricSettingsViewImpl.class);
        bindPresenterWidget(TraceSettingsPresenter.class,
                TraceSettingsView.class,
                TraceSettingsViewImpl.class);
    }
}

