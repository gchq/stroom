/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.search.solr.client.view;

import stroom.index.shared.LuceneFieldTypes;
import stroom.item.client.SelectionBox;
import stroom.query.api.datasource.FieldType;
import stroom.search.solr.client.presenter.SolrIndexFieldEditPresenter.SolrIndexFieldEditView;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.List;

public class SolrIndexFieldEditViewImpl extends ViewImpl implements SolrIndexFieldEditView {

    private final Widget widget;

    @UiField
    SelectionBox<FieldType> type;
    @UiField
    TextBox fieldName;
    @UiField
    SelectionBox<String> fieldType;
    @UiField
    TextBox defaultValue;
    @UiField
    CustomCheckBox stored;
    @UiField
    CustomCheckBox indexed;
    @UiField
    CustomCheckBox uninvertible;
    @UiField
    CustomCheckBox docValues;
    @UiField
    CustomCheckBox multiValued;
    @UiField
    CustomCheckBox required;
    @UiField
    CustomCheckBox omitNorms;
    @UiField
    CustomCheckBox omitTermFreqAndPositions;
    @UiField
    CustomCheckBox omitPositions;
    @UiField
    CustomCheckBox termVectors;
    @UiField
    CustomCheckBox termPositions;
    @UiField
    CustomCheckBox termOffsets;
    @UiField
    CustomCheckBox termPayloads;
    @UiField
    CustomCheckBox sortMissingFirst;
    @UiField
    CustomCheckBox sortMissingLast;

    @Inject
    public SolrIndexFieldEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        type.addItems(LuceneFieldTypes.FIELD_TYPES);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        type.focus();
    }

    @Override
    public FieldType getType() {
        return type.getValue();
    }

    @Override
    public void setType(final FieldType type) {
        this.type.setValue(type);
    }

    @Override
    public String getFieldName() {
        return fieldName.getText();
    }

    @Override
    public void setFieldName(final String fieldName) {
        this.fieldName.setText(fieldName);
    }

    @Override
    public String getFieldType() {
        return fieldType.getValue();
    }

    @Override
    public void setFieldType(final String fieldType) {
        this.fieldType.setValue(fieldType);
    }

    @Override
    public String getDefaultValue() {
        return defaultValue.getText();
    }

    @Override
    public void setDefaultValue(final String defaultValue) {
        this.defaultValue.setText(defaultValue);
    }

    @Override
    public boolean isStored() {
        return stored.getValue();
    }

    @Override
    public void setStored(final boolean stored) {
        this.stored.setValue(stored);
    }

    @Override
    public boolean isIndexed() {
        return indexed.getValue();
    }

    @Override
    public void setIndexed(final boolean indexed) {
        this.indexed.setValue(indexed);
    }

    @Override
    public boolean isUninvertible() {
        return uninvertible.getValue();
    }

    @Override
    public void setUninvertible(final boolean uninvertible) {
        this.uninvertible.setValue(uninvertible);
    }

    @Override
    public boolean isDocValues() {
        return docValues.getValue();
    }

    @Override
    public void setDocValues(final boolean docValues) {
        this.docValues.setValue(docValues);
    }

    @Override
    public boolean isMultiValued() {
        return multiValued.getValue();
    }

    @Override
    public void setMultiValued(final boolean multiValued) {
        this.multiValued.setValue(multiValued);
    }

    @Override
    public boolean isRequired() {
        return required.getValue();
    }

    @Override
    public void setRequired(final boolean required) {
        this.required.setValue(required);
    }

    @Override
    public boolean isOmitNorms() {
        return omitNorms.getValue();
    }

    @Override
    public void setOmitNorms(final boolean omitNorms) {
        this.omitNorms.setValue(omitNorms);
    }

    @Override
    public boolean isOmitTermFreqAndPositions() {
        return omitTermFreqAndPositions.getValue();
    }

    @Override
    public void setOmitTermFreqAndPositions(final boolean omitTermFreqAndPositions) {
        this.omitTermFreqAndPositions.setValue(omitTermFreqAndPositions);
    }

    @Override
    public boolean isOmitPositions() {
        return omitPositions.getValue();
    }

    @Override
    public void setOmitPositions(final boolean omitPositions) {
        this.omitPositions.setValue(omitPositions);
    }

    @Override
    public boolean isTermVectors() {
        return termVectors.getValue();
    }

    @Override
    public void setTermVectors(final boolean termVectors) {
        this.termVectors.setValue(termVectors);
    }

    @Override
    public boolean isTermPositions() {
        return termPositions.getValue();
    }

    @Override
    public void setTermPositions(final boolean termPositions) {
        this.termPositions.setValue(termPositions);
    }

    @Override
    public boolean isTermOffsets() {
        return termOffsets.getValue();
    }

    @Override
    public void setTermOffsets(final boolean termOffsets) {
        this.termOffsets.setValue(termOffsets);
    }

    @Override
    public boolean isTermPayloads() {
        return termPayloads.getValue();
    }

    @Override
    public void setTermPayloads(final boolean termPayloads) {
        this.termPayloads.setValue(termPayloads);
    }

    @Override
    public boolean isSortMissingFirst() {
        return sortMissingFirst.getValue();
    }

    @Override
    public void setSortMissingFirst(final boolean sortMissingFirst) {
        this.sortMissingFirst.setValue(sortMissingFirst);
    }

    @Override
    public boolean isSortMissingLast() {
        return sortMissingLast.getValue();
    }

    @Override
    public void setSortMissingLast(final boolean sortMissingLast) {
        this.sortMissingLast.setValue(sortMissingLast);
    }

    @Override
    public void setFieldTypes(final List<String> fieldTypes) {
        this.fieldType.clear();
        this.fieldType.addItems(fieldTypes);
    }

    public interface Binder extends UiBinder<Widget, SolrIndexFieldEditViewImpl> {

    }
}
