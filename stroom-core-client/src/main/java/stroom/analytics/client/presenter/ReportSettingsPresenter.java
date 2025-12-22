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

package stroom.analytics.client.presenter;

import stroom.analytics.client.presenter.ReportSettingsPresenter.ReportSettingsView;
import stroom.analytics.shared.ReportDoc;
import stroom.analytics.shared.ReportSettings;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class ReportSettingsPresenter
        extends DocumentEditPresenter<ReportSettingsView, ReportDoc>
        implements DirtyUiHandlers {

    @Inject
    public ReportSettingsPresenter(final EventBus eventBus, final ReportSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final ReportDoc document,
                          final boolean readOnly) {
        if (document != null &&
            document.getReportSettings() != null &&
            document.getReportSettings().getFileType() != null) {
            getView().setFileType(document.getReportSettings().getFileType());
        }
    }

    @Override
    protected ReportDoc onWrite(final ReportDoc document) {
        final ReportSettings reportSettings = ReportSettings
                .builder()
                .fileType(getView().getFileType())
                .build();
        return document.copy().reportSettings(reportSettings).build();
    }

    //
//    public boolean downloadAllTables() {
//        return getView().downloadAllTables();
//    }
//
//    public void setShowDownloadAll(final boolean show) {
//        getView().setShowDownloadAll(show);
//    }
//
//    public boolean isSample() {
//        return getView().isSample();
//    }
//
//    public int getPercent() {
//        return getView().getPercent();
//    }


    @Override
    public void onDirty() {
        setDirty(true);
    }

    public interface ReportSettingsView extends View, Focus, HasUiHandlers<DirtyUiHandlers> {

        DownloadSearchResultFileType getFileType();

        void setFileType(DownloadSearchResultFileType fileType);

//        boolean downloadAllTables();
//
//        void setShowDownloadAll(boolean show);
//
//        boolean isSample();
//
//        int getPercent();
    }
}
