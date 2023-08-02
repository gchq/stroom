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

package stroom.dashboard.client.table;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.dashboard.client.table.DownloadPresenter.DownloadView;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.item.client.ItemListBox;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class DownloadViewImpl extends ViewImpl implements DownloadView {

    private final Widget widget;
    @UiField
    ItemListBox<DownloadSearchResultFileType> fileType;
    @UiField
    TickBox downloadAllTables;
    @UiField
    TickBox sample;
    @UiField
    ValueSpinner percent;

    @Inject
    public DownloadViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        percent.setMax(100);
        percent.setMin(1);
        percent.setValue(100);
        percent.setEnabled(false);

        fileType.addItem(DownloadSearchResultFileType.EXCEL);
        fileType.addItem(DownloadSearchResultFileType.CSV);
        fileType.addItem(DownloadSearchResultFileType.TSV);

        fileType.setSelectedItem(DownloadSearchResultFileType.EXCEL);

        downloadAllTables.setEnabled(isExcelFileTypeSelected());
        fileType.addSelectionHandler(event -> {
            downloadAllTables.setEnabled(isExcelFileTypeSelected());
            if (!isExcelFileTypeSelected()) {
                downloadAllTables.setBooleanValue(false);
            }
        });
    }

    private boolean isExcelFileTypeSelected() {
        return DownloadSearchResultFileType.EXCEL.equals(fileType.getSelectedItem());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public DownloadSearchResultFileType getFileType() {
        return fileType.getSelectedItem();
    }

    @Override
    public boolean downloadAllTables() {
        return downloadAllTables.getBooleanValue();
    }

    @Override
    public boolean isSample() {
        return sample.getBooleanValue();
    }

    @Override
    public int getPercent() {
        return percent.getValue();
    }

    @UiHandler("sample")
    public void onChange(final ValueChangeEvent<TickBoxState> event) {
        percent.setEnabled(sample.getBooleanValue());
    }

    public interface Binder extends UiBinder<Widget, DownloadViewImpl> {

    }
}
