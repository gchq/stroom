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

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.AbstractSettingsPresenter.SettingsView;
import stroom.analytics.client.presenter.ReportSettingsPresenter.ReportSettingsView;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ReportSettings;
import stroom.config.global.client.presenter.ConfigDefaultSetter;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class ReportSettingsPresenter
        extends AbstractSettingsPresenter<ReportSettingsView, ReportDoc> {

    private final DocSelectionBoxPresenter aiSummaryModelPresenter;

    @Inject
    public ReportSettingsPresenter(final EventBus eventBus,
                                   final ReportSettingsView view,
                                   final DocSelectionBoxPresenter errorFeedPresenter,
                                   final DocSelectionBoxPresenter aiSummaryModelPresenter,
                                   final UiConfigCache uiConfigCache,
                                   final ConfigDefaultSetter configDefaultSetter) {
        super(eventBus, view, errorFeedPresenter, uiConfigCache, configDefaultSetter);
        this.aiSummaryModelPresenter = aiSummaryModelPresenter;

        view.setAiSummaryModelView(aiSummaryModelPresenter.getView());
        aiSummaryModelPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        // Use, not View - a report needs to be able to ask the model, not to read its settings.
        aiSummaryModelPresenter.setRequiredPermissions(DocumentPermission.USE);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(aiSummaryModelPresenter.addDataSelectionHandler(event -> onChange()));
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final ReportDoc document,
                          final boolean readOnly) {
        super.onRead(docRef, document, readOnly);
        getView().setFileType(NullSafe.getOrElse(
                document,
                ReportDoc::getReportSettings,
                ReportSettings::getFileType,
                DownloadSearchResultFileType.EXCEL));
        getView().setSendEmptyReports(NullSafe.getOrElse(
                document,
                ReportDoc::getReportSettings,
                ReportSettings::isSendEmptyReports,
                false));
        getView().setAiSummaryEnabled(NullSafe.getOrElse(
                document,
                ReportDoc::getReportSettings,
                ReportSettings::isAiSummaryEnabled,
                false));
        aiSummaryModelPresenter.setSelectedEntityReference(NullSafe.get(
                document,
                ReportDoc::getReportSettings,
                ReportSettings::getAiSummaryModel), true);
        // Shown empty rather than pre-filled with the default, so that reading and writing the document
        // round trips. Filling the box in would make an untouched report look dirty as soon as it was
        // opened, and would bake the default text into the document on the next save.
        getView().setAiSummaryPrompt(NullSafe.getOrElse(
                document,
                ReportDoc::getReportSettings,
                ReportSettings::getAiSummaryPrompt,
                ""));
    }

    @Override
    protected ReportDoc onWrite(final ReportDoc document) {
        final ReportSettings reportSettings = ReportSettings
                .builder()
                .fileType(getView().getFileType())
                .sendEmptyReports(getView().isSendEmptyReports())
                .aiSummaryEnabled(getView().isAiSummaryEnabled())
                .aiSummaryModel(aiSummaryModelPresenter.getSelectedEntityReference())
                // Blank means "use the default", which is what a null prompt says.
                .aiSummaryPrompt(NullSafe.isBlankString(getView().getAiSummaryPrompt())
                        ? null
                        : getView().getAiSummaryPrompt())
                .build();
        return document.copy()
                .reportSettings(reportSettings)
                .errorFeed(getErrorFeed())
                .build();
    }

    // --------------------------------------------------------------------------------


    public interface ReportSettingsView extends SettingsView, Focus {

        DownloadSearchResultFileType getFileType();

        void setFileType(DownloadSearchResultFileType fileType);

        boolean isSendEmptyReports();

        void setSendEmptyReports(boolean sendEmptyReports);

        boolean isAiSummaryEnabled();

        void setAiSummaryEnabled(boolean aiSummaryEnabled);

        void setAiSummaryModelView(View view);

        String getAiSummaryPrompt();

        void setAiSummaryPrompt(String aiSummaryPrompt);
    }
}
