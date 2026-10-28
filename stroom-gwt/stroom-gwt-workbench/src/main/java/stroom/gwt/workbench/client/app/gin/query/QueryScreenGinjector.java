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

import stroom.ai.client.component.AskStroomAiController;
import stroom.analytics.client.component.AnalyticRuleEditor;
import stroom.analytics.client.component.ReportEditor;
import stroom.annotation.client.component.FindAnnotationController;
import stroom.data.client.DataDisplaySupport;
import stroom.gwt.workbench.client.app.gin.EntityScreenModule;
import stroom.gwt.workbench.client.app.gin.ScreenViewsModule;
import stroom.gwt.workbench.client.app.gin.TaskScreenModule;
import stroom.gwt.workbench.client.app.screen.ScreenGinjector;
import stroom.hyperlink.client.HyperlinkEventHandlerImpl;
import stroom.iframe.client.component.IFrameContentController;
import stroom.planb.client.component.PlanBEditor;
import stroom.query.client.DateTimeSettingsFactory;
import stroom.query.client.ResultStoreModel;
import stroom.query.client.component.QueryDocEditor;
import stroom.query.client.component.QueryResultTableController;
import stroom.statistics.impl.sql.client.component.StatisticsDataSourceEditor;
import stroom.view.client.component.ViewDocEditor;

import com.google.gwt.inject.client.GinModules;

/// A [ScreenGinjector] for the query batch's stories (`App/Editors/QueryEditor`, the View,
/// Plan B, analytic rule, report and statistic store editors, and the query result table and
/// visualisation), creating Stroom's controllers and components as Stroom's GIN modules do, as
/// `AppScreenGinjector` does for the pilot's stories. Create one each time the story renders.
@GinModules({
        ScreenViewsModule.class,
        EntityScreenModule.class,
        TaskScreenModule.class,
        AlertScreenModule.class,
})
public interface QueryScreenGinjector extends ScreenGinjector {

    /// @return A View's editor, as `ViewPlugin` opens it.
    ViewDocEditor getViewDocEditor();

    /// @return A Plan B store's editor, as `PlanBPlugin` opens it.
    PlanBEditor getPlanBEditor();

    /// @return A Query's editor, as `QueryPlugin` opens it.
    QueryDocEditor getQueryDocEditor();

    /// @return A statistic store's editor, as `StatisticsPlugin` opens it.
    StatisticsDataSourceEditor getStatisticsDataSourceEditor();

    /// @return An analytic rule's editor, as `AnalyticsPlugin` opens it.
    AnalyticRuleEditor getAnalyticRuleEditor();

    /// @return A query's result table, as a query pane or an analytic rule's Shards tab has.
    QueryResultTableController getQueryResultTable();

    /// @return A report's editor, as `ReportPlugin` opens it.
    ReportEditor getReportEditor();

    /// @return The 'Choose Annotation' dialog, kept by the app and shown when
    ///         `ShowFindAnnotationEvent` fires.
    FindAnnotationController getFindAnnotationController();

    /// @return Stroom's handler of `HyperlinkEvent`s (a table's links), which Stroom creates eagerly.
    HyperlinkEventHandlerImpl getHyperlinkEventHandler();

    /// @return A link's page as a tab, as `HyperlinkEventHandlerImpl` opens a `tab` link.
    IFrameContentController getIFrameContentController();

    /// @return Stroom's handler of `ShowDataEvent`s (a data link's dialog), which Stroom creates
    /// eagerly.
    DataDisplaySupport getDataDisplaySupport();

    /// @return The date and time settings of searches, from the user's preferences.
    DateTimeSettingsFactory getDateTimeSettingsFactory();

    /// @return The client of the result store resource, for a dashboard's `SearchModel`.
    ResultStoreModel getResultStoreModel();

    /// @return The 'Ask Stroom AI' chat, which a results table's 'Ask Stroom AI' button opens (show
    /// it with `AskStroomAiChat`).
    AskStroomAiController getAskStroomAiController();
}
