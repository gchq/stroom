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

package stroom.pipeline.client.component;

import stroom.item.client.SelectionBox;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A text converter's Settings tab: how its content is interpreted.
public class TextConverterSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<TextConverterType> converterType;

    private Runnable onChange = () -> {
    };

    /// Makes an empty tab.
    public TextConverterSettings() {
        initWidget(BINDER.createAndBindUi(this));
        converterType.addItem(TextConverterType.NONE);
        converterType.addItem(TextConverterType.DATA_SPLITTER);
        converterType.addItem(TextConverterType.XML_FRAGMENT);
        converterType.addValueChangeHandler(event -> onChange.run());
    }

    /// Shows a text converter's settings.
    ///
    /// @param textConverter The text converter.
    public void read(final TextConverterDoc textConverter) {
        converterType.setValue(textConverter.getConverterType());
    }

    /// Writes the settings shown into a copy of a text converter.
    ///
    /// @param textConverter The text converter.
    /// @return A copy of it with the settings shown.
    public TextConverterDoc write(final TextConverterDoc textConverter) {
        return textConverter.copy().converterType(converterType.getValue()).build();
    }

    /// @return The converter type shown, which the user may have changed.
    public TextConverterType getConverterType() {
        return converterType.getValue();
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        converterType.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TextConverterSettings> {

    }
}
