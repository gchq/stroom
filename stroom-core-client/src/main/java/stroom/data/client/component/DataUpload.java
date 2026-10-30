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

package stroom.data.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.component.FileChooser;
import stroom.widget.selection.client.SelectionBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Data to upload to a feed: its meta data, stream type, effective date and file.
public class DataUpload extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<String> type;
    @UiField
    FileChooser fileChooser;
    @UiField
    DateTimeBox effective;
    @UiField
    TextArea metaData;

    /// Makes the form, empty.
    public DataUpload() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the keyboard focus on the meta data.
    public void focus() {
        metaData.setFocus(true);
    }

    /// @return The file chooser, for its controller to attach to.
    public FileChooser getFileChooser() {
        return fileChooser;
    }

    /// @return The effective date box, for its controller to attach to.
    public DateTimeBox getEffectiveBox() {
        return effective;
    }

    /// @return The effective date chosen, or `null` for none.
    public Long getEffectiveDate() {
        return effective.getValue();
    }

    /// Lists the stream types, one chosen.
    ///
    /// @param types The stream types.
    /// @param type  The one chosen.
    public void setTypes(final List<String> types, final String type) {
        this.type.clear();
        this.type.addItems(types);
        this.type.setValue(type);
    }

    /// @return The stream type chosen, or `null` for none.
    public String getType() {
        return type.getValue();
    }

    /// @return The meta data typed, one `Key:Value` entry per line.
    public String getMetaData() {
        return metaData.getText();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DataUpload> {

    }
}
