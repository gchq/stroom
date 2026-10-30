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

package stroom.importexport.client.component;

import stroom.importexport.shared.ImportState;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The content of the Confirm Import dialog: what the import will do to each item, with the
/// import's settings.
public class ImportConfigConfirm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DataGrid<ImportState> dataGrid;
    @UiField
    TickBox enableFilters;
    @UiField
    DateTimeBox enableFrom;
    @UiField
    TickBox useImportNames;
    @UiField
    TickBox useImportFolders;
    @UiField
    DropDownBox rootFolder;

    private Consumer<Boolean> onUseImportNames = useImportNames -> {
    };
    private Consumer<Boolean> onUseImportFolders = useImportFolders -> {
    };

    /// Makes the content, with nothing ticked.
    public ImportConfigConfirm() {
        initWidget(BINDER.createAndBindUi(this));
        useImportNames.addClickHandler(this::onUseImportNames);
        useImportFolders.addClickHandler(this::onUseImportFolders);
    }

    /// Puts the focus on Enable Processor Filters.
    public void focus() {
        enableFilters.setFocus(true);
    }

    /// @return The items, for the controller to give its columns and rows.
    public DataGrid<ImportState> getDataGrid() {
        return dataGrid;
    }

    /// @return The Import To Root Folder picker, for its controller to attach to.
    public DropDownBox getRootFolderBox() {
        return rootFolder;
    }

    /// @return The Process From box, for its controller to attach to.
    public DateTimeBox getEnableFromBox() {
        return enableFrom;
    }

    /// @return When imported processor filters process from.
    public Long getEnableFromDate() {
        return enableFrom.getValue();
    }

    /// @param date When imported processor filters process from.
    public void setEnableFromDate(final Long date) {
        enableFrom.setValue(date);
    }

    /// @return Whether imported processor filters are switched on.
    public boolean isEnableFilters() {
        return enableFilters.getValue();
    }

    /// @param onUseImportNames Given whether Use Import Names is ticked, when it is clicked.
    public void setOnUseImportNames(final Consumer<Boolean> onUseImportNames) {
        this.onUseImportNames = onUseImportNames;
    }

    /// @param onUseImportFolders Given whether Use Import Folders is ticked, when it is clicked.
    public void setOnUseImportFolders(final Consumer<Boolean> onUseImportFolders) {
        this.onUseImportFolders = onUseImportFolders;
    }

    private void onUseImportNames(final ClickEvent event) {
        onUseImportNames.accept(useImportNames.getValue());
    }

    private void onUseImportFolders(final ClickEvent event) {
        onUseImportFolders.accept(useImportFolders.getValue());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ImportConfigConfirm> {

    }
}
