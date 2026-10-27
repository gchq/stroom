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

package stroom.processor.client.component;

import stroom.item.client.SelectionBox;
import stroom.processor.shared.ProcessorFilterChange;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

/// The content of the batch processor filter dialog: a change for every processor filter a
/// filter matches, with Run As User shown only for Set run as user.
public class BatchProcessorFilterEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<ProcessorFilterChange> change;
    @UiField
    FormGroup userRefLabel;
    @UiField
    DropDownBox userRef;

    /// Makes the content, with Enable chosen.
    public BatchProcessorFilterEdit() {
        initWidget(BINDER.createAndBindUi(this));
        change.addItems(ProcessorFilterChange.LIST);
        change.setValue(ProcessorFilterChange.ENABLE);
    }

    /// Puts the focus on the change.
    public void focus() {
        change.focus();
    }

    /// @return The change chosen.
    public ProcessorFilterChange getChange() {
        return change.getValue();
    }

    /// @return The Run As User picker, for its controller to attach to.
    public DropDownBox getUserRefBox() {
        return userRef;
    }

    @UiHandler("change")
    void onChange(final ValueChangeEvent<ProcessorFilterChange> e) {
        userRefLabel.setVisible(ProcessorFilterChange.SET_RUN_AS_USER.equals(change.getValue()));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, BatchProcessorFilterEdit> {

    }
}
