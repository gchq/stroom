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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.ReportSettingsPresenter.ReportSettingsView;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.item.client.SelectionBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ReportSettingsViewImpl extends ViewWithUiHandlers<DirtyUiHandlers> implements ReportSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<DownloadSearchResultFileType> fileType;
    @UiField
    CustomCheckBox sendEmptyReports;

    @Inject
    public ReportSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        fileType.addItem(DownloadSearchResultFileType.EXCEL);
        fileType.addItem(DownloadSearchResultFileType.CSV);
        fileType.addItem(DownloadSearchResultFileType.TSV);
        fileType.setValue(DownloadSearchResultFileType.EXCEL);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        fileType.focus();
    }

    @Override
    public DownloadSearchResultFileType getFileType() {
        return fileType.getValue();
    }

    @Override
    public void setFileType(final DownloadSearchResultFileType fileType) {
        this.fileType.setValue(fileType);
    }

    @Override
    public boolean isSendEmptyReports() {
        return sendEmptyReports.getValue();
    }

    @Override
    public void setSendEmptyReports(final boolean sendEmptyReports) {
        this.sendEmptyReports.setValue(sendEmptyReports);
    }

    @UiHandler("fileType")
    public void onFileTypeChange(final ValueChangeEvent<DownloadSearchResultFileType> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("sendEmptyReports")
    public void onSendEmptyReports(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    public interface Binder extends UiBinder<Widget, ReportSettingsViewImpl> {

    }
}
