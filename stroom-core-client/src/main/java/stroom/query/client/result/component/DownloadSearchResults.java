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

package stroom.query.client.result.component;

import stroom.analytics.shared.ReportSettings;
import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Download Options dialog's content, for a table's results: the file type, whether all
/// the dashboard's tables go in it, and whether only a sample does.
public class DownloadSearchResults extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<DownloadSearchResultFileType> fileType;
    @UiField
    FormGroup downloadAll;
    @UiField
    TickBox downloadAllTables;
    @UiField
    TickBox sample;
    @UiField
    ValueSpinner percent;

    /// Makes the content.
    public DownloadSearchResults() {
        initWidget(BINDER.createAndBindUi(this));
        sample.setOnChange(value -> onSampleChange());

        // ValueSpinner's setValue is overloaded, which UiBinder can't choose between
        percent.setValue(100);

        fileType.addItems(DownloadSearchResultFileType.asSortedList());
        fileType.setValue(ReportSettings.DEFAULT_FILE_TYPE);

        downloadAllTables.setEnabled(isExcelFileTypeSelected());
        fileType.addValueChangeHandler(event -> {
            downloadAllTables.setEnabled(isExcelFileTypeSelected());
            if (!isExcelFileTypeSelected()) {
                downloadAllTables.setValue(false);
            }
        });
    }

    private boolean isExcelFileTypeSelected() {
        return DownloadSearchResultFileType.EXCEL.equals(fileType.getValue());
    }


    /// Puts the focus on the file type.
    public void focus() {
        fileType.focus();
    }

    /// @return The file type chosen.
    public DownloadSearchResultFileType getFileType() {
        return fileType.getValue();
    }

    /// @return Whether all the dashboard's tables go in the file.
    public boolean downloadAllTables() {
        return downloadAllTables.getValue();
    }

    /// @param show Whether Download All Tables is offered.
    public void setShowDownloadAll(final boolean show) {
        downloadAll.setVisible(show);
    }

    /// @return Whether only a sample goes in the file.
    public boolean isSample() {
        return sample.getValue();
    }

    /// @return The percentage of the results in a sample.
    public int getPercent() {
        return percent.getIntValue();
    }

    private void onSampleChange() {
        percent.setEnabled(sample.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DownloadSearchResults> {

    }
}
