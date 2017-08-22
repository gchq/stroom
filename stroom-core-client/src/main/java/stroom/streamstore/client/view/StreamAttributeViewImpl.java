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

package stroom.streamstore.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.entity.client.EntityItemListBox;
import stroom.item.client.ItemListBox;
import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.streamstore.client.presenter.StreamAttributePresenter.StreamAttributeView;

import java.util.List;

public class StreamAttributeViewImpl extends ViewImpl implements StreamAttributeView {
    private final Widget widget;

    @UiField(provided = true)
    EntityItemListBox key;
    @UiField(provided = true)
    ItemListBox<Condition> condition;
    @UiField
    TextBox value;

    @Inject
    public StreamAttributeViewImpl(final Binder binder) {
        key = new EntityItemListBox("", false);
        condition = new ItemListBox<>("");
        condition.addItems(Condition.SIMPLE_CONDITIONS);
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setKeys(final List<DocRef> keys) {
        key.addItems(keys);
    }

    @Override
    public DocRef getKey() {
        return key.getSelectedItem();
    }

    @Override
    public void setKey(final DocRef key) {
        this.key.setSelectedItem(key);
    }

    @Override
    public Condition getCondition() {
        return condition.getSelectedItem();
    }

    @Override
    public void setCondition(final Condition condition) {
        this.condition.setSelectedItem(condition);
    }

    @Override
    public String getValue() {
        return value.getValue();
    }

    @Override
    public void setValue(final String value) {
        this.value.setValue(value);
    }

    public interface Binder extends UiBinder<Widget, StreamAttributeViewImpl> {
    }
}
