/*
 * Copyright 2020 Crown Copyright
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

package stroom.index.client.component;

import stroom.index.shared.LuceneFieldTypes;
import stroom.item.client.SelectionBox;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The New and Edit Field dialog's content, for a Lucene index: the field's type, name, how it
/// is indexed and, for a dense vector, its options.
public class IndexFieldEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<FieldType> type;
    @UiField
    TextBox name;
    @UiField
    TickBox stored;
    @UiField
    TickBox indexed;
    @UiField
    TickBox positions;
    @UiField
    SelectionBox<AnalyzerType> analyser;
    @UiField
    TickBox caseSensitive;
    @UiField
    SimplePanel denseVectorOptions;

    private Runnable onTypeChange = () -> {
    };

    /// Makes the content.
    public IndexFieldEditor() {
        initWidget(BINDER.createAndBindUi(this));
        type.addItems(LuceneFieldTypes.FIELD_TYPES);
        analyser.addItems(AnalyzerType.values());
    }

    /// Puts the focus on the type.
    public void focus() {
        type.focus();
    }

    /// @return The field's type.
    public FieldType getType() {
        return type.getValue();
    }

    /// @param type The field's type.
    public void setType(final FieldType type) {
        this.type.setValue(type);
    }

    /// @return The name entered.
    public String getFieldName() {
        return name.getText();
    }

    /// @param fieldName The field's name.
    public void setFieldName(final String fieldName) {
        name.setText(fieldName);
    }

    /// @return Whether the field's value is stored.
    public boolean isStored() {
        return stored.getValue();
    }

    /// @param stored Whether the field's value is stored.
    public void setStored(final boolean stored) {
        this.stored.setValue(stored);
    }

    /// @return Whether the field is indexed.
    public boolean isIndexed() {
        return indexed.getValue();
    }

    /// @param indexed Whether the field is indexed.
    public void setIndexed(final boolean indexed) {
        this.indexed.setValue(indexed);
    }

    /// @return Whether term positions are kept.
    public boolean isTermPositions() {
        return positions.getValue();
    }

    /// @param termPositions Whether term positions are kept.
    public void setTermPositions(final boolean termPositions) {
        positions.setValue(termPositions);
    }

    /// @return The analyser.
    public AnalyzerType getAnalyzerType() {
        return analyser.getValue();
    }

    /// @param analyzerType The analyser.
    public void setAnalyzerType(final AnalyzerType analyzerType) {
        this.analyser.setValue(analyzerType);
    }

    /// @return Whether matching is case sensitive.
    public boolean isCaseSensitive() {
        return caseSensitive.getValue();
    }

    /// @param caseSensitive Whether matching is case sensitive.
    public void setCaseSensitive(final boolean caseSensitive) {
        this.caseSensitive.setValue(caseSensitive);
    }

    /// @param options The dense vector options, which their controller makes with their own
    ///                content.
    public void setDenseVectorOptions(final IsWidget options) {
        this.denseVectorOptions.setWidget(options);
    }

    /// @param visible Whether the dense vector options are shown.
    public void setDenseVectorOptionsVisible(final boolean visible) {
        this.denseVectorOptions.setVisible(visible);
    }

    /// @param onTypeChange Run when the type is changed.
    public void setOnTypeChange(final Runnable onTypeChange) {
        this.onTypeChange = onTypeChange;
    }

    @UiHandler("type")
    void onType(final ValueChangeEvent<?> event) {
        onTypeChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IndexFieldEditor> {

    }
}
