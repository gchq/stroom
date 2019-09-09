/*
 * Copyright 2017 Crown Copyright
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

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.item.client.ItemListBox;
import stroom.item.client.StringListBox;
import stroom.search.solr.client.presenter.SolrIndexFieldEditPresenter.SolrIndexFieldEditView;
import stroom.search.solr.shared.SolrIndexFieldType;
import stroom.widget.tickbox.client.view.TickBox;

import java.util.List;

public class SolrIndexFieldEditViewImpl extends ViewImpl implements SolrIndexFieldEditView {
    private final Widget widget;

    @UiField
    ItemListBox<SolrIndexFieldType> fieldUse;
    @UiField
    TextBox fieldName;
    @UiField
    StringListBox fieldType;
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

    @Inject
    public SolrIndexFieldEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        fieldUse.addItems(SolrIndexFieldType.values());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public SolrIndexFieldType getFieldUse() {
        return fieldUse.getSelectedItem();
    }

    @Override
    public void setFieldUse(final SolrIndexFieldType fieldUse) {
        this.fieldUse.setSelectedItem(fieldUse);
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
        return fieldType.getSelected();
    }

    @Override
    public void setFieldType(final String fieldType) {
        this.fieldType.setSelected(fieldType);
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
        return stored.getBooleanValue();
    }

    @Override
    public void setStored(final boolean stored) {
        this.stored.setBooleanValue(stored);
    }

    @Override
    public boolean isIndexed() {
        return indexed.getBooleanValue();
    }

    @Override
    public void setIndexed(final boolean indexed) {
        this.indexed.setBooleanValue(indexed);
    }

    @Override
    public boolean isUninvertible() {
        return uninvertible.getBooleanValue();
    }

    @Override
    public void setUninvertible(final boolean uninvertible) {
        this.uninvertible.setBooleanValue(uninvertible);
    }

    @Override
    public boolean isDocValues() {
        return docValues.getBooleanValue();
    }

    @Override
    public void setDocValues(final boolean docValues) {
        this.docValues.setBooleanValue(docValues);
    }

    @Override
    public boolean isMultiValued() {
        return multiValued.getBooleanValue();
    }

    @Override
    public void setMultiValued(final boolean multiValued) {
        this.multiValued.setBooleanValue(multiValued);
    }

    @Override
    public boolean isRequired() {
        return required.getBooleanValue();
    }

    @Override
    public void setRequired(final boolean required) {
        this.required.setBooleanValue(required);
    }

    @Override
    public boolean isOmitNorms() {
        return omitNorms.getBooleanValue();
    }

    @Override
    public void setOmitNorms(final boolean omitNorms) {
        this.omitNorms.setBooleanValue(omitNorms);
    }

    @Override
    public boolean isOmitTermFreqAndPositions() {
        return omitTermFreqAndPositions.getBooleanValue();
    }

    @Override
    public void setOmitTermFreqAndPositions(final boolean omitTermFreqAndPositions) {
        this.omitTermFreqAndPositions.setBooleanValue(omitTermFreqAndPositions);
    }

    @Override
    public boolean isOmitPositions() {
        return omitPositions.getBooleanValue();
    }

    @Override
    public void setOmitPositions(final boolean omitPositions) {
        this.omitPositions.setBooleanValue(omitPositions);
    }

    @Override
    public boolean isTermVectors() {
        return termVectors.getBooleanValue();
    }

    @Override
    public void setTermVectors(final boolean termVectors) {
        this.termVectors.setBooleanValue(termVectors);
    }

    @Override
    public boolean isTermPositions() {
        return termPositions.getBooleanValue();
    }

    @Override
    public void setTermPositions(final boolean termPositions) {
        this.termPositions.setBooleanValue(termPositions);
    }

    @Override
    public boolean isTermOffsets() {
        return termOffsets.getBooleanValue();
    }

    @Override
    public void setTermOffsets(final boolean termOffsets) {
        this.termOffsets.setBooleanValue(termOffsets);
    }

    @Override
    public boolean isTermPayloads() {
        return termPayloads.getBooleanValue();
    }

    @Override
    public void setTermPayloads(final boolean termPayloads) {
        this.termPayloads.setBooleanValue(termPayloads);
    }

    @Override
    public boolean isSortMissingFirst() {
        return sortMissingFirst.getBooleanValue();
    }

    @Override
    public void setSortMissingFirst(final boolean sortMissingFirst) {
        this.sortMissingFirst.setBooleanValue(sortMissingFirst);
    }

    @Override
    public boolean isSortMissingLast() {
        return sortMissingLast.getBooleanValue();
    }

    @Override
    public void setSortMissingLast(final boolean sortMissingLast) {
        this.sortMissingLast.setBooleanValue(sortMissingLast);
    }

    @Override
    public void setFieldTypes(final List<String> fieldTypes) {
        this.fieldType.clear();
        this.fieldType.addItems(fieldTypes);
    }

    public interface Binder extends UiBinder<Widget, SolrIndexFieldEditViewImpl> {
    }
}
