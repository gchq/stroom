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

package stroom.receive.content.client.view;

import stroom.item.client.SelectionBox;
import stroom.receive.content.client.presenter.ContentTemplateEditPresenter.ContentTemplateEditView;
import stroom.receive.content.shared.TemplateType;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ContentTemplateEditViewImpl extends ViewImpl implements ContentTemplateEditView {

    private final Widget widget;

    @UiField
    SimplePanel expression;
    @UiField
    TextBox name;
    @UiField
    TextArea description;
    @UiField
    SelectionBox<TemplateType> templateTypeSelectionBox;
    @UiField
    SimplePanel pipeline;

    @Inject
    public ContentTemplateEditViewImpl(final ContentTemplateEditViewImpl.Binder binder) {
        widget = binder.createAndBindUi(this);

        final List<TemplateType> templateTypes = Arrays.stream(TemplateType.values())
                .sorted()
                .collect(Collectors.toList());

        templateTypeSelectionBox.addItems(templateTypes);

        // TODO @AT There must be a better way of setting the focus than having to use a timer
        new Timer() {
            @Override
            public void run() {
                name.setFocus(true);
                name.setTabIndex(0);
            }
        }.schedule(100);
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
    public void setExpressionView(final View view) {
        expression.setWidget(view.asWidget());
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
    public String getDescription() {
        return description.getText();
    }

    @Override
    public void setDescription(final String description) {
        this.description.setText(description);
    }

    @Override
    public TemplateType getTemplateType() {
        return templateTypeSelectionBox.getValue();
    }

    @Override
    public void setTemplateType(final TemplateType templateType) {
        templateTypeSelectionBox.setValue(templateType);
    }

    @Override
    public void setPipelineSelector(final View view) {
        final Widget w = view.asWidget();
        w.getElement().getStyle().setWidth(100, Unit.PCT);
        w.getElement().getStyle().setMargin(0, Unit.PX);
        pipeline.setWidget(w);
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ContentTemplateEditViewImpl> {

    }
}
