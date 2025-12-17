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

package stroom.dashboard.client.text;

import stroom.dashboard.client.text.TextPresenter.TextView;
import stroom.data.client.view.ClassificationLabel;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitor;
import stroom.widget.button.client.FabButton;
import stroom.widget.spinner.client.SpinnerLarge;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class TextViewImpl extends ViewWithUiHandlers<TextUiHandlers> implements TextView {

    private final Widget widget;
    @UiField
    SimplePanel content;
    @UiField(provided = true)
    ClassificationLabel classification;
    @UiField
    FabButton steppingButton;
    @UiField
    SpinnerLarge spinner;

    @Inject
    public TextViewImpl(final Binder binder, final ClassificationLabel classification) {
        this.classification = classification;
        widget = binder.createAndBindUi(this);
        spinner.setVisible(false);
        steppingButton.setIcon(SvgImage.STEPPING);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setContent(final View view) {
        this.content.setWidget(view.asWidget());
    }

    @Override
    public void setClassification(final String text) {
        classification.setClassification(text);
    }

    @Override
    public void setSteppingVisible(final boolean visible) {
        steppingButton.setVisible(visible);
    }

    @UiHandler("steppingButton")
    public void onSteppingClick(final ClickEvent e) {
        if (getUiHandlers() != null) {
            getUiHandlers().beginStepping();
        }
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return spinner.createTaskMonitor();
    }

    public interface Binder extends UiBinder<Widget, TextViewImpl> {

    }
}
