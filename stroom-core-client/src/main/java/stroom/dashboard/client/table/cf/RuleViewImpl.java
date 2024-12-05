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

package stroom.dashboard.client.table.cf;

import stroom.item.client.SelectionBox;
import stroom.query.api.v2.ConditionalFormattingStyle;
import stroom.query.api.v2.ConditionalFormattingType;
import stroom.widget.button.client.Button;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class RuleViewImpl extends ViewWithUiHandlers<RuleUiHandlers> implements RulePresenter.RuleView {

    private final Widget widget;

    @UiField
    SimplePanel expression;
    @UiField
    CustomCheckBox hide;
    @UiField
    SelectionBox<ConditionalFormattingType> formattingType;
    @UiField
    SelectionBox<ConditionalFormattingStyle> formattingStyle;
    @UiField
    Button editCustomStyle;
    @UiField
    CustomCheckBox enabled;

    @Inject
    public RuleViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        formattingType.addItems(ConditionalFormattingType.LIST);
        formattingType.addStyleName("conditionalFormatTypeSelection");

        formattingStyle.setRenderFunction(style -> ConditionalFormattingSwatchUtil
                .createSwatch(formattingType.getValue(), style));
        formattingStyle.addItems(ConditionalFormattingStyle.LIST);
        formattingStyle.addStyleName("conditionalFormatStyleSelection");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setExpressionView(final View view) {
        this.expression.setWidget(view.asWidget());
    }

    @Override
    public boolean isHide() {
        return this.hide.getValue();
    }

    @Override
    public void setHide(final boolean hide) {
        this.hide.setValue(hide);
    }

    @Override
    public void setFormattingType(final ConditionalFormattingType formattingType) {
        this.formattingType.setValue(formattingType);
        updateVisibility();
    }

    @Override
    public ConditionalFormattingType getFormattingType() {
        return formattingType.getValue();
    }

    @Override
    public void setFormattingStyle(final ConditionalFormattingStyle formattingStyle) {
        this.formattingStyle.setValue(formattingStyle);
    }

    @Override
    public ConditionalFormattingStyle getFormattingStyle() {
        return formattingStyle.getValue();
    }

    @Override
    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    private void updateVisibility() {
        final ConditionalFormattingStyle style = formattingStyle.getValue();
        formattingStyle.clear();
        formattingStyle.addItems(ConditionalFormattingStyle.LIST);
        formattingStyle.setValue(style);

        final ConditionalFormattingType type = formattingType.getValue();
        final boolean custom = type == null || ConditionalFormattingType.CUSTOM.equals(type);
        formattingStyle.setEnabled(!custom);
        editCustomStyle.setEnabled(custom);
    }

    @UiHandler("formattingType")
    public void onFormattingType(final ValueChangeEvent<ConditionalFormattingType> e) {
        updateVisibility();
    }

    @UiHandler("editCustomStyle")
    public void onEditCustomStyle(final ClickEvent e) {
        getUiHandlers().onEditCustomStyle();
    }

    public interface Binder extends UiBinder<Widget, RuleViewImpl> {

    }
}
