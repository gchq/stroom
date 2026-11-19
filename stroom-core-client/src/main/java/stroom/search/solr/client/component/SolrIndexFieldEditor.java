/*
 * Copyright 2019 Crown Copyright
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

package stroom.search.solr.client.component;

import stroom.index.shared.LuceneFieldTypes;
import stroom.query.api.datasource.FieldType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The New and Edit Field dialog's content, for a Solr index: the field's type, name, Solr
/// field type and default value, and how Solr stores and indexes it.
public class SolrIndexFieldEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SelectionBox<FieldType> type;
    @UiField
    TextBox fieldName;
    @UiField
    SelectionBox<String> fieldType;
    @UiField
    TextBox defaultValue;
    @UiField
    TickBox stored;
    @UiField
    TickBox indexed;
    @UiField
    TickBox uninvertible;
    @UiField
    TickBox docValues;
    @UiField
    TickBox multiValued;
    @UiField
    TickBox required;
    @UiField
    TickBox omitNorms;
    @UiField
    TickBox omitTermFreqAndPositions;
    @UiField
    TickBox omitPositions;
    @UiField
    TickBox termVectors;
    @UiField
    TickBox termPositions;
    @UiField
    TickBox termOffsets;
    @UiField
    TickBox termPayloads;
    @UiField
    TickBox sortMissingFirst;
    @UiField
    TickBox sortMissingLast;

    /// Makes the content.
    public SolrIndexFieldEditor() {
        initWidget(BINDER.createAndBindUi(this));

        type.addItems(LuceneFieldTypes.FIELD_TYPES);
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
        return fieldName.getText();
    }

    /// @param fieldName The field's name.
    public void setFieldName(final String fieldName) {
        this.fieldName.setText(fieldName);
    }

    /// @return The Solr field type.
    public String getFieldType() {
        return fieldType.getValue();
    }

    /// @param fieldType The Solr field type.
    public void setFieldType(final String fieldType) {
        this.fieldType.setValue(fieldType);
    }

    /// @return The default value entered.
    public String getDefaultValue() {
        return defaultValue.getText();
    }

    /// @param defaultValue The field's default value.
    public void setDefaultValue(final String defaultValue) {
        this.defaultValue.setText(defaultValue);
    }

    /// @return Whether stored is ticked.
    public boolean isStored() {
        return stored.getValue();
    }

    /// @param stored Whether stored is ticked.
    public void setStored(final boolean stored) {
        this.stored.setValue(stored);
    }

    /// @return Whether indexed is ticked.
    public boolean isIndexed() {
        return indexed.getValue();
    }

    /// @param indexed Whether indexed is ticked.
    public void setIndexed(final boolean indexed) {
        this.indexed.setValue(indexed);
    }

    /// @return Whether uninvertible is ticked.
    public boolean isUninvertible() {
        return uninvertible.getValue();
    }

    /// @param uninvertible Whether uninvertible is ticked.
    public void setUninvertible(final boolean uninvertible) {
        this.uninvertible.setValue(uninvertible);
    }

    /// @return Whether doc values is ticked.
    public boolean isDocValues() {
        return docValues.getValue();
    }

    /// @param docValues Whether doc values is ticked.
    public void setDocValues(final boolean docValues) {
        this.docValues.setValue(docValues);
    }

    /// @return Whether multi valued is ticked.
    public boolean isMultiValued() {
        return multiValued.getValue();
    }

    /// @param multiValued Whether multi valued is ticked.
    public void setMultiValued(final boolean multiValued) {
        this.multiValued.setValue(multiValued);
    }

    /// @return Whether required is ticked.
    public boolean isRequired() {
        return required.getValue();
    }

    /// @param required Whether required is ticked.
    public void setRequired(final boolean required) {
        this.required.setValue(required);
    }

    /// @return Whether omit norms is ticked.
    public boolean isOmitNorms() {
        return omitNorms.getValue();
    }

    /// @param omitNorms Whether omit norms is ticked.
    public void setOmitNorms(final boolean omitNorms) {
        this.omitNorms.setValue(omitNorms);
    }

    /// @return Whether omit term freq and positions is ticked.
    public boolean isOmitTermFreqAndPositions() {
        return omitTermFreqAndPositions.getValue();
    }

    /// @param omitTermFreqAndPositions Whether omit term freq and positions is ticked.
    public void setOmitTermFreqAndPositions(final boolean omitTermFreqAndPositions) {
        this.omitTermFreqAndPositions.setValue(omitTermFreqAndPositions);
    }

    /// @return Whether omit positions is ticked.
    public boolean isOmitPositions() {
        return omitPositions.getValue();
    }

    /// @param omitPositions Whether omit positions is ticked.
    public void setOmitPositions(final boolean omitPositions) {
        this.omitPositions.setValue(omitPositions);
    }

    /// @return Whether term vectors is ticked.
    public boolean isTermVectors() {
        return termVectors.getValue();
    }

    /// @param termVectors Whether term vectors is ticked.
    public void setTermVectors(final boolean termVectors) {
        this.termVectors.setValue(termVectors);
    }

    /// @return Whether term positions is ticked.
    public boolean isTermPositions() {
        return termPositions.getValue();
    }

    /// @param termPositions Whether term positions is ticked.
    public void setTermPositions(final boolean termPositions) {
        this.termPositions.setValue(termPositions);
    }

    /// @return Whether term offsets is ticked.
    public boolean isTermOffsets() {
        return termOffsets.getValue();
    }

    /// @param termOffsets Whether term offsets is ticked.
    public void setTermOffsets(final boolean termOffsets) {
        this.termOffsets.setValue(termOffsets);
    }

    /// @return Whether term payloads is ticked.
    public boolean isTermPayloads() {
        return termPayloads.getValue();
    }

    /// @param termPayloads Whether term payloads is ticked.
    public void setTermPayloads(final boolean termPayloads) {
        this.termPayloads.setValue(termPayloads);
    }

    /// @return Whether sort missing first is ticked.
    public boolean isSortMissingFirst() {
        return sortMissingFirst.getValue();
    }

    /// @param sortMissingFirst Whether sort missing first is ticked.
    public void setSortMissingFirst(final boolean sortMissingFirst) {
        this.sortMissingFirst.setValue(sortMissingFirst);
    }

    /// @return Whether sort missing last is ticked.
    public boolean isSortMissingLast() {
        return sortMissingLast.getValue();
    }

    /// @param sortMissingLast Whether sort missing last is ticked.
    public void setSortMissingLast(final boolean sortMissingLast) {
        this.sortMissingLast.setValue(sortMissingLast);
    }

    /// @param fieldTypes The field types Solr offers.
    public void setFieldTypes(final List<String> fieldTypes) {
        this.fieldType.clear();
        this.fieldType.addItems(fieldTypes);
    }

    // --------------------------------------------------------------------------------

    interface Binder extends UiBinder<Widget, SolrIndexFieldEditor> {
    }
}
