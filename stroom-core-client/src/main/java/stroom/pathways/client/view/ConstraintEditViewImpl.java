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

package stroom.pathways.client.view;

import stroom.item.client.SelectionBox;
import stroom.pathways.client.presenter.ConstraintEditPresenter.ConstraintEditView;
import stroom.pathways.shared.pathway.ConstraintValueType;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class ConstraintEditViewImpl extends ViewImpl implements ConstraintEditView {

    private final Widget widget;

    @UiField
    TextBox name;
    @UiField
    SelectionBox<ConstraintValueType> type;
    @UiField
    TextBox value;
    @UiField
    CustomCheckBox optional;

    @Inject
    public ConstraintEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        type.addItems(ConstraintValueType.values());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        name.setFocus(true);
    }

    @Override
    public String getName() {
        return name.getText();
    }

    @Override
    public void setName(final String fieldName) {
        name.setText(fieldName);
    }

    @Override
    public ConstraintValueType getType() {
        return type.getValue();
    }

    @Override
    public void setType(final ConstraintValueType type) {
        this.type.setValue(type);
    }

    @Override
    public String getValue() {
        return value.getValue();
    }

    @Override
    public void setValue(final String value) {
        this.value.setValue(value);
    }

    @Override
    public boolean isOptional() {
        return optional.getValue();
    }

    @Override
    public void setOptional(final boolean optional) {
        this.optional.setValue(optional);
    }

    public interface Binder extends UiBinder<Widget, ConstraintEditViewImpl> {

    }
}
