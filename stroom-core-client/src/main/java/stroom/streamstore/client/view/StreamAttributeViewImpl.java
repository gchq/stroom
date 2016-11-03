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

package stroom.streamstore.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import stroom.query.shared.Condition;
import stroom.entity.client.EntityItemListBox;
import stroom.item.client.ItemListBox;
import stroom.streamstore.client.presenter.StreamAttributePresenter.StreamAttributeView;

public class StreamAttributeViewImpl extends ViewImpl implements StreamAttributeView {
    public interface Binder extends UiBinder<Widget, StreamAttributeViewImpl> {
    }

    private final Widget widget;

    @UiField(provided = true)
    ItemListBox<Condition> streamAttributeCondition;
    @UiField(provided = true)
    EntityItemListBox streamAttributeKey;
    @UiField
    TextBox streamAttributeValue;

    @Inject
    public StreamAttributeViewImpl(final Binder binder) {
        streamAttributeKey = new EntityItemListBox("", false);
        streamAttributeCondition = new ItemListBox<Condition>("");
        streamAttributeCondition.addItems(Condition.SIMPLE_CONDITIONS);
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public EntityItemListBox getStreamAttributeKey() {
        return streamAttributeKey;
    }

    @Override
    public ItemListBox<Condition> getStreamAttributeCondition() {
        return streamAttributeCondition;
    }

    @Override
    public String getStreamAttributeValue() {
        return streamAttributeValue.getText();
    }

    @Override
    public void setStreamAttributeValue(final String value) {
        streamAttributeValue.setText(value);
    }
}
