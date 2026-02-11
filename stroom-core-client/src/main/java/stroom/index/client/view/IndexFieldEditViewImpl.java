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

package stroom.index.client.view;

import stroom.document.client.event.DirtyUiHandlers;
import stroom.index.client.presenter.IndexFieldEditPresenter.IndexFieldEditView;
import stroom.index.shared.LuceneFieldTypes;
import stroom.item.client.SelectionBox;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class IndexFieldEditViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements IndexFieldEditView {

    private final Widget widget;
    @UiField
    SelectionBox<FieldType> type;
    @UiField
    TextBox name;
    @UiField
    CustomCheckBox stored;
    @UiField
    CustomCheckBox indexed;
    @UiField
    CustomCheckBox positions;
    @UiField
    SelectionBox<AnalyzerType> analyser;
    @UiField
    CustomCheckBox caseSensitive;
    @UiField
    SimplePanel denseVectorOptions;

    @Inject
    public IndexFieldEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        type.addItems(LuceneFieldTypes.FIELD_TYPES);
        analyser.addItems(AnalyzerType.values());
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
        return name.getText();
    }

    @Override
    public void setFieldName(final String fieldName) {
        name.setText(fieldName);
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
    public boolean isTermPositions() {
        return positions.getValue();
    }

    @Override
    public void setTermPositions(final boolean termPositions) {
        positions.setValue(termPositions);
    }

    @Override
    public AnalyzerType getAnalyzerType() {
        return analyser.getValue();
    }

    @Override
    public void setAnalyzerType(final AnalyzerType analyzerType) {
        this.analyser.setValue(analyzerType);
    }

    @Override
    public boolean isCaseSensitive() {
        return caseSensitive.getValue();
    }

    @Override
    public void setCaseSensitive(final boolean caseSensitive) {
        this.caseSensitive.setValue(caseSensitive);
    }

    @Override
    public void setDenseVectorOptions(final View view) {
        this.denseVectorOptions.setWidget(view.asWidget());
    }

    @Override
    public void setDenseVectorOptionsVisible(final boolean visible) {
        this.denseVectorOptions.setVisible(visible);
    }

    @UiHandler("type")
    public void onChange(final ValueChangeEvent<?> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    public interface Binder extends UiBinder<Widget, IndexFieldEditViewImpl> {

    }
}
