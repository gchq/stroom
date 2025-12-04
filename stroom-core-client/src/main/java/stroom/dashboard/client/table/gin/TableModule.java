/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.dashboard.client.table.AskStroomAiPresenter;
import stroom.dashboard.client.table.AskStroomAiPresenter.AskStroomAiView;
import stroom.dashboard.client.table.AskStroomAiViewImpl;
import stroom.dashboard.client.table.BasicTableSettingsPresenter;
import stroom.dashboard.client.table.BasicTableSettingsPresenter.BasicTableSettingsView;
import stroom.dashboard.client.table.BasicTableSettingsViewImpl;
import stroom.dashboard.client.table.ColumnFilterPresenter;
import stroom.dashboard.client.table.ColumnFilterPresenter.ColumnFilterView;
import stroom.dashboard.client.table.ColumnFilterViewImpl;
import stroom.dashboard.client.table.ColumnFunctionEditorPresenter;
import stroom.dashboard.client.table.ColumnFunctionEditorPresenter.ColumnFunctionEditorView;
import stroom.dashboard.client.table.ColumnFunctionEditorViewImpl;
import stroom.dashboard.client.table.ColumnValuesFilterPresenter;
import stroom.dashboard.client.table.ColumnValuesFilterPresenter.ColumnValuesFilterView;
import stroom.dashboard.client.table.ColumnValuesFilterViewImpl;
import stroom.dashboard.client.table.DownloadPresenter;
import stroom.dashboard.client.table.DownloadPresenter.DownloadView;
import stroom.dashboard.client.table.DownloadViewImpl;
import stroom.dashboard.client.table.FormatPresenter;
import stroom.dashboard.client.table.FormatPresenter.FormatView;
import stroom.dashboard.client.table.FormatViewImpl;
import stroom.dashboard.client.table.IncludeExcludeFilterPresenter;
import stroom.dashboard.client.table.IncludeExcludeFilterPresenter.IncludeExcludeFilterView;
import stroom.dashboard.client.table.IncludeExcludeFilterViewImpl;
import stroom.dashboard.client.table.RenameColumnPresenter;
import stroom.dashboard.client.table.RenameColumnPresenter.RenameColumnView;
import stroom.dashboard.client.table.RenameColumnViewImpl;
import stroom.dashboard.client.table.TablePlugin;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.client.table.TablePresenter.TableView;
import stroom.dashboard.client.table.TableViewImpl;
import stroom.dashboard.client.table.cf.CustomRowStylePresenter;
import stroom.dashboard.client.table.cf.CustomRowStyleViewImpl;
import stroom.dashboard.client.table.cf.EditExpressionPresenter;
import stroom.dashboard.client.table.cf.EditExpressionViewImpl;
import stroom.dashboard.client.table.cf.RulePresenter;
import stroom.dashboard.client.table.cf.RuleViewImpl;
import stroom.dashboard.client.table.cf.RulesPresenter;
import stroom.dashboard.client.table.cf.RulesViewImpl;
import stroom.query.client.presenter.TimeZones;

import com.gwtplatform.mvp.client.gin.AbstractPresenterModule;

public class TableModule extends AbstractPresenterModule {

    @Override
    protected void configure() {
        bind(TimeZones.class).asEagerSingleton();

        bind(TablePlugin.class).asEagerSingleton();


        bindPresenterWidget(TablePresenter.class, TableView.class, TableViewImpl.class);
        bindPresenterWidget(
                BasicTableSettingsPresenter.class,
                BasicTableSettingsView.class,
                BasicTableSettingsViewImpl.class);
        bindPresenterWidget(
                ColumnFunctionEditorPresenter.class,
                ColumnFunctionEditorView.class,
                ColumnFunctionEditorViewImpl.class);
        bindPresenterWidget(FormatPresenter.class, FormatView.class, FormatViewImpl.class);
        bindPresenterWidget(IncludeExcludeFilterPresenter.class,
                IncludeExcludeFilterView.class,
                IncludeExcludeFilterViewImpl.class);
        bindPresenterWidget(ColumnFilterPresenter.class, ColumnFilterView.class, ColumnFilterViewImpl.class);
        bindPresenterWidget(DownloadPresenter.class, DownloadView.class, DownloadViewImpl.class);
        bindPresenterWidget(RenameColumnPresenter.class, RenameColumnView.class, RenameColumnViewImpl.class);
        bindPresenterWidget(AskStroomAiPresenter.class, AskStroomAiView.class, AskStroomAiViewImpl.class);

        bindPresenterWidget(
                EditExpressionPresenter.class,
                EditExpressionPresenter.EditExpressionView.class,
                EditExpressionViewImpl.class);
        bindPresenterWidget(RulesPresenter.class, RulesPresenter.RulesView.class, RulesViewImpl.class);
        bindPresenterWidget(RulePresenter.class, RulePresenter.RuleView.class, RuleViewImpl.class);
        bindPresenterWidget(
                CustomRowStylePresenter.class,
                CustomRowStylePresenter.CustomRowStyleView.class,
                CustomRowStyleViewImpl.class);
        bindPresenterWidget(
                ColumnValuesFilterPresenter.class,
                ColumnValuesFilterView.class,
                ColumnValuesFilterViewImpl.class);
    }
}
