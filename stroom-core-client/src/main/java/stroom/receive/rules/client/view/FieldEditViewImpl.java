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

package stroom.receive.rules.client.view;

import stroom.item.client.SelectionBox;
import stroom.query.api.datasource.FieldType;
import stroom.receive.rules.client.presenter.FieldEditPresenter.FieldEditView;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.EnumSet;
import java.util.Set;

public class FieldEditViewImpl extends ViewImpl implements FieldEditView {

    private static final Set<FieldType> VALID_FIELD_TYPES = EnumSet.of(
            FieldType.TEXT,
            FieldType.DATE,
            FieldType.INTEGER,
            FieldType.LONG,
            FieldType.FLOAT,
            FieldType.DOUBLE,
            FieldType.BOOLEAN,
            FieldType.IPV4_ADDRESS);

    private final Widget widget;
    @UiField
    SelectionBox<FieldType> type;
    @UiField
    TextBox name;

    @Inject
    public FieldEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        type.addItems(VALID_FIELD_TYPES);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public FieldType getFieldType() {
        return type.getValue();
    }

    @Override
    public void setFieldType(final FieldType type) {
        this.type.setValue(type);
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void focus() {
        type.focus();
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, FieldEditViewImpl> {

    }
}
