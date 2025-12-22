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

package stroom.annotation.client;

import stroom.annotation.client.AnnotationTagEditPresenter.AnnotationTagEditView;
import stroom.dashboard.client.table.cf.ConditionalFormattingSwatchUtil;
import stroom.item.client.SelectionBox;
import stroom.query.api.ConditionalFormattingStyle;
import stroom.query.api.ConditionalFormattingType;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AnnotationTagEditViewImpl
        extends ViewWithUiHandlers<AnnotationTagEditUiHandlers>
        implements AnnotationTagEditView {

    private final Widget widget;

    @UiField
    TextBox name;
    @UiField
    FormGroup styleGroup;
    @UiField
    SelectionBox<ConditionalFormattingStyle> formattingStyle;
    @UiField
    Button editPermissions;

    @Inject
    public AnnotationTagEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        formattingStyle.setRenderFunction(style -> ConditionalFormattingSwatchUtil
                .createSwatch(ConditionalFormattingType.BACKGROUND, style, null));
        formattingStyle.addItems(ConditionalFormattingStyle.LIST);
        formattingStyle.setValue(ConditionalFormattingStyle.NONE);
        formattingStyle.addStyleName("conditionalFormatStyleSelection");
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
        return this.name.getText();
    }

    @Override
    public void setName(final String name) {
        this.name.setText(name);
    }

    @Override
    public void showStyle(final boolean show) {
        styleGroup.setVisible(show);
    }

    @Override
    public ConditionalFormattingStyle getStyle() {
        return formattingStyle.getValue();
    }

    @Override
    public void setStyle(final ConditionalFormattingStyle style) {
        formattingStyle.setValue(style);
    }

    @UiHandler("editPermissions")
    public void onEditPermissions(final ClickEvent e) {
        getUiHandlers().onPermissions();
    }

    public interface Binder extends UiBinder<Widget, AnnotationTagEditViewImpl> {

    }
}
