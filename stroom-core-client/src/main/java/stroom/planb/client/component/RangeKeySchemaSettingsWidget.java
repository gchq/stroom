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

package stroom.planb.client.component;

import stroom.item.client.SelectionBox;
import stroom.planb.shared.RangeKeySchema;
import stroom.planb.shared.RangeType;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

public class RangeKeySchemaSettingsWidget extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    SelectionBox<RangeType> rangeType;

    /// Makes the settings, at their defaults.
    public RangeKeySchemaSettingsWidget() {
        initWidget(BINDER.createAndBindUi(this));
        rangeType.addItems(RangeType.ORDERED_LIST);
        setKeySchema(new RangeKeySchema.Builder().build());
    }

    public RangeKeySchema getKeySchema() {
        return new RangeKeySchema.Builder()
                .rangeType(rangeType.getValue())
                .build();
    }

    public void setKeySchema(final RangeKeySchema rangeKeySchema) {
        final RangeKeySchema schema = new RangeKeySchema.Builder(rangeKeySchema).build();
        rangeType.setValue(schema.getRangeType());
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        rangeType.setReadOnly(readOnly);
    }

    @UiHandler("rangeType")
    public void onRangeType(final ValueChangeEvent<RangeType> event) {
        changed();
    }

    /// Sets what is told when the user changes a setting.
    ///
    /// @param onChange Run each time, or null for nothing.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange != null
                ? onChange
                : () -> {
                };
    }

    // Says that the user changed a setting
    private void changed() {
        onChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, RangeKeySchemaSettingsWidget> {

    }
}
