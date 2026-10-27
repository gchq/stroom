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

import stroom.dashboard.client.table.BasicTableSettingsPresenter;
import stroom.dashboard.client.table.BasicTableSettingsPresenter.BasicTableSettingsView;
import stroom.dashboard.client.table.BasicTableSettingsViewImpl;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.client.table.TablePresenter.TableView;
import stroom.dashboard.client.table.TableViewImpl;
import stroom.dashboard.client.table.cf.RulePresenter;
import stroom.dashboard.client.table.cf.RuleViewImpl;
import stroom.dashboard.client.table.cf.RulesPresenter;
import stroom.dashboard.client.table.cf.RulesViewImpl;
import stroom.widget.presenter.client.gin.AbstractPresenterModule;

/// The presenter and view bindings of Stroom's `TableModule`
/// (`stroom/dashboard/client/table/gin/TableModule.java`), without its plugins and app services.
/// A story shows a screen that Stroom opens on an app-wide event by adding a handler for
/// the event, as the screen's area plugin does in Stroom.
public class TableScreenModule extends AbstractPresenterModule {

    /// Binds the presenters and views.
    @Override
    protected void configure() {
        bindPresenterWidget(TablePresenter.class,
                TableView.class,
                TableViewImpl.class);
        bindPresenterWidget(BasicTableSettingsPresenter.class,
                BasicTableSettingsView.class,
                BasicTableSettingsViewImpl.class);
        bindPresenterWidget(RulesPresenter.class,
                RulesPresenter.RulesView.class,
                RulesViewImpl.class);
        bindPresenterWidget(RulePresenter.class,
                RulePresenter.RuleView.class,
                RuleViewImpl.class);
    }
}
