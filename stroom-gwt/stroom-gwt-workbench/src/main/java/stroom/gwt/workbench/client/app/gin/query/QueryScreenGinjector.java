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

import stroom.analytics.client.presenter.AnalyticRulePresenter;
import stroom.analytics.client.presenter.ReportPresenter;
import stroom.annotation.client.FindAnnotationPresenter;
import stroom.data.client.presenter.DataDisplaySupport;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ExplorerScreenModule;
import stroom.gwt.workbench.client.app.gin.MonitoringScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.SecurityScreenModule;
import stroom.gwt.workbench.client.app.gin.TaskScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.hyperlink.client.HyperlinkEventHandlerImpl;
import stroom.planb.client.presenter.PlanBPresenter;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.QueryDocPresenter;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.statistics.impl.sql.client.presenter.StatisticsDataSourcePresenter;
import stroom.view.client.presenter.ViewPresenter;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] for the query batch's stories (`App/Editors/QueryEditor`, the View,
/// Plan B, analytic rule, report and statistic store editors, and the query result table and
/// visualisation), creating Stroom's presenters and views as Stroom's GIN modules do, as
/// `AppScreenGinjector` does for the pilot's stories. Create one each time the story renders.
@GinModules({
        ScreenViewsModule.class,
        EntityScreenModule.class,
        ExplorerScreenModule.class,
        SecurityScreenModule.class,
        MonitoringScreenModule.class,
        TaskScreenModule.class,
        PipelineScreenModule.class,
        ViewScreenModule.class,
        PlanBScreenModule.class,
        QueryScreenModule.class,
        TableScreenModule.class,
        AnnotationScreenModule.class,
        StreamStoreScreenModule.class,
        DashboardQueryScreenModule.class,
        ActivityScreenModule.class,
        AlertScreenModule.class,
        QueryExtrasScreenModule.class,
        StatisticsScreenModule.class,
        AnalyticsScreenModule.class,
        ReportScreenModule.class,
})
public interface QueryScreenGinjector extends ScreenGinjector {

    /// @return A View's editor tab, as `ViewPlugin` creates it.
    ViewPresenter getViewPresenter();

    /// @return A Plan B store's editor tab, as `PlanBPlugin` creates it.
    PlanBPresenter getPlanBPresenter();

    /// @return A Query's editor tab, as `QueryPlugin` creates it.
    QueryDocPresenter getQueryDocPresenter();

    /// @return A statistic store's editor tab, as `StatisticsPlugin` creates it.
    StatisticsDataSourcePresenter getStatisticsDataSourcePresenter();

    /// @return An analytic rule's editor tab, as `AnalyticsPlugin` creates it.
    AnalyticRulePresenter getAnalyticRulePresenter();

    /// @return A report's editor tab, as `ReportPlugin` creates it.
    ReportPresenter getReportPresenter();

    /// @return The 'Choose Annotation' dialog, shown by firing `ShowFindAnnotationEvent`.
    FindAnnotationPresenter getFindAnnotationPresenter();

    /// @return Stroom's handler of `HyperlinkEvent`s (a table's links), which Stroom creates eagerly.
    HyperlinkEventHandlerImpl getHyperlinkEventHandler();

    /// @return Stroom's handler of `ShowDataEvent`s (a data link's dialog), which Stroom creates
    /// eagerly.
    DataDisplaySupport getDataDisplaySupport();

    /// @return The date and time settings of searches, from the user's preferences.
    DateTimeSettingsFactory getDateTimeSettingsFactory();

    /// @return The client of the result store resource, for a dashboard's `SearchModel`.
    ResultStoreModel getResultStoreModel();
}
