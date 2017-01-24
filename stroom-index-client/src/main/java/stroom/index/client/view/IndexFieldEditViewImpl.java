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

package stroom.index.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.index.client.presenter.IndexFieldEditPresenter.IndexFieldEditView;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFieldType;
import stroom.item.client.ItemListBox;
import stroom.widget.tickbox.client.view.TickBox;

public class IndexFieldEditViewImpl extends ViewImpl implements IndexFieldEditView {
    private final Widget widget;
    @UiField
    ItemListBox<IndexFieldType> type;
    @UiField
    TextBox name;
    @UiField
    TickBox stored;
    @UiField
    TickBox indexed;
    @UiField
    TickBox positions;
    @UiField
    ItemListBox<AnalyzerType> analyser;
    @UiField
    TickBox caseSensitive;

    @Inject
    public IndexFieldEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        type.addItems(IndexFieldType.values());
        analyser.addItems(AnalyzerType.values());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public IndexFieldType getFieldUse() {
        return type.getSelectedItem();
    }

    @Override
    public void setFieldUse(final IndexFieldType fieldUse) {
        type.setSelectedItem(fieldUse);
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
    public boolean isTermPositions() {
        return positions.getBooleanValue();
    }

    @Override
    public void setTermPositions(final boolean termPositions) {
        positions.setBooleanValue(termPositions);
    }

    @Override
    public AnalyzerType getAnalyzerType() {
        return analyser.getSelectedItem();
    }

    @Override
    public void setAnalyzerType(final AnalyzerType analyzerType) {
        this.analyser.setSelectedItem(analyzerType);
    }

    @Override
    public boolean isCaseSensitive() {
        return caseSensitive.getBooleanValue();
    }

    @Override
    public void setCaseSensitive(final boolean caseSensitive) {
        this.caseSensitive.setBooleanValue(caseSensitive);
    }

    public interface Binder extends UiBinder<Widget, IndexFieldEditViewImpl> {
    }
}
