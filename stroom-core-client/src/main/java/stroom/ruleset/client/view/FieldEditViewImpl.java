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
 *
 */

package stroom.ruleset.client.view;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.item.client.ItemListBox;
import stroom.ruleset.client.presenter.FieldEditPresenter.FieldEditView;

public class FieldEditViewImpl extends ViewImpl implements FieldEditView {
    private final Widget widget;
    @UiField
    ItemListBox<DataSourceFieldType> type;
    @UiField
    TextBox name;
    @Inject
    public FieldEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        type.addItems(DataSourceFieldType.values());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public DataSourceFieldType getType() {
        return type.getSelectedItem();
    }

    @Override
    public void setType(final DataSourceFieldType type) {
        this.type.setSelectedItem(type);
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    public interface Binder extends UiBinder<Widget, FieldEditViewImpl> {
    }
}
