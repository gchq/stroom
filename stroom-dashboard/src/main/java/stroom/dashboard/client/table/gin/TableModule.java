/*
 * Copyright 2016 Crown Copyright
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

package stroom.dashboard.client.table.gin;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;
import stroom.dashboard.client.table.BasicTableSettingsPresenter;
import stroom.dashboard.client.table.BasicTableSettingsPresenter.BasicTableSettingsView;
import stroom.dashboard.client.table.BasicTableSettingsViewImpl;
import stroom.dashboard.client.table.DownloadPresenter;
import stroom.dashboard.client.table.DownloadPresenter.DownloadView;
import stroom.dashboard.client.table.DownloadViewImpl;
import stroom.dashboard.client.table.ExpressionPresenter;
import stroom.dashboard.client.table.ExpressionPresenter.ExpressionView;
import stroom.dashboard.client.table.ExpressionViewImpl;
import stroom.dashboard.client.table.FilterPresenter;
import stroom.dashboard.client.table.FilterPresenter.FilterView;
import stroom.dashboard.client.table.FilterViewImpl;
import stroom.dashboard.client.table.FormatPresenter;
import stroom.dashboard.client.table.FormatPresenter.FormatView;
import stroom.dashboard.client.table.FormatViewImpl;
import stroom.dashboard.client.table.TablePlugin;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.client.table.TablePresenter.TableView;
import stroom.dashboard.client.table.TableViewImpl;
import stroom.dashboard.client.table.TimeZones;

public class TableModule extends AbstractPresenterModule {
    @Override
    protected void configure() {
        bind(TimeZones.class).asEagerSingleton();

        bind(TablePlugin.class).asEagerSingleton();
        bindPresenterWidget(TablePresenter.class, TableView.class, TableViewImpl.class);
        bindPresenterWidget(BasicTableSettingsPresenter.class, BasicTableSettingsView.class,
                BasicTableSettingsViewImpl.class);
        bindPresenterWidget(ExpressionPresenter.class, ExpressionView.class, ExpressionViewImpl.class);
        bindPresenterWidget(FormatPresenter.class, FormatView.class, FormatViewImpl.class);
        bindPresenterWidget(FilterPresenter.class, FilterView.class, FilterViewImpl.class);
        bindPresenterWidget(DownloadPresenter.class, DownloadView.class, DownloadViewImpl.class);
    }
}
