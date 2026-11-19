/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ReportSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// A report's Settings tab ([ReportSettingsTab]), with its AI summary model's picker and its error
/// feed ([ErrorFeedController]).
public class ReportSettingsTabController extends Controller {

    private final ReportSettingsTab view;
    private final DocSelectionController aiSummaryModel;
    private final ErrorFeedController errorFeed;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The tab.
    /// @param docSelectionProvider Makes the AI summary model's picker's controller.
    /// @param errorFeedProvider    Makes the error feed's controller.
    @Inject
    public ReportSettingsTabController(final EventBus eventBus,
                                       final ReportSettingsTab view,
                                       final Provider<DocSelectionController> docSelectionProvider,
                                       final Provider<ErrorFeedController> errorFeedProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        aiSummaryModel = make(docSelectionProvider);
        aiSummaryModel.attach(view.getAiSummaryModelBox());
        aiSummaryModel.setIncludedTypes(OpenAIModelDoc.TYPE);
        // Use, not View - a report needs to be able to ask the model, not to read its settings.
        aiSummaryModel.setRequiredPermissions(DocumentPermission.USE);
        errorFeed = make(errorFeedProvider).attach(view.getErrorFeedBox());
        view.setSetDefaultVisible(errorFeed.isSetDefaultAllowed());
        view.setOnSetDefault(errorFeed::setDefault);

        view.setOnChange(this::markChanged);
        own(aiSummaryModel.addSelectionListener(event -> markChanged()));
        own(errorFeed.addSelectionListener(e -> markChanged()));
        watch(Contexts.READ_ONLY, readOnly -> {
            view.setReadOnly(readOnly);
            aiSummaryModel.setReadOnly(readOnly);
            errorFeed.setReadOnly(readOnly);
        });
    }

    /// Shows a report's settings.
    ///
    /// @param docRef The report.
    /// @param report Its settings.
    public void read(final DocRef docRef, final ReportDoc report) {
        errorFeed.read(docRef, report.getErrorFeed());
        view.read(report.getReportSettings());
        aiSummaryModel.setSelectedEntityReference(NullSafe.get(
                report,
                ReportDoc::getReportSettings,
                ReportSettings::getAiSummaryModel), true);
    }

    /// Writes the settings shown into a copy of a report.
    ///
    /// @param report The report.
    /// @return A copy of it with the settings shown.
    public ReportDoc write(final ReportDoc report) {
        final ReportSettings reportSettings = view.write(ReportSettings.builder())
                .aiSummaryModel(aiSummaryModel.getSelectedEntityReference())
                .build();
        return report.copy()
                .reportSettings(reportSettings)
                .errorFeed(errorFeed.getErrorFeed())
                .build();
    }
}
