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

package stroom.pipeline.stepping.client.component;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.widget.component.client.Component;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The "Set Location" dialog's content: the stream, part and record to step to, the part and record
/// counted from 1, as the user sees them. Its markup is `StepLocationPicker.ui.xml`;
/// [StepLocationPickerController] shows it.
public class StepLocationPicker extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ValueSpinner metaId;
    @UiField
    ValueSpinner partNo;
    @UiField
    ValueSpinner recordNo;

    /// Makes the dialog's content.
    public StepLocationPicker() {
        initWidget(BINDER.createAndBindUi(this));
        metaId.setMax(Long.MAX_VALUE);
        partNo.setMax(Long.MAX_VALUE);
        recordNo.setMax(Long.MAX_VALUE);
    }

    /// Puts the focus on the stream.
    public void focus() {
        metaId.focus();
    }

    /// @return The location entered, its part and record zero based.
    public StepLocation getStepLocation() {
        return new StepLocation(metaId.getValue(), partNo.getValue() - 1, recordNo.getValue() - 1);
    }

    /// @param stepLocation The location to show, its part and record zero based.
    public void setStepLocation(final StepLocation stepLocation) {
        metaId.setValue(stepLocation.getMetaId());
        partNo.setValue(stepLocation.getPartIndex() + 1);
        recordNo.setValue(stepLocation.getRecordIndex() + 1);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, StepLocationPicker> {

    }
}
