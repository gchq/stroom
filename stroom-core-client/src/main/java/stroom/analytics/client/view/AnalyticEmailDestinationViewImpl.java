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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.AnalyticEmailDestinationPresenter.AnalyticEmailDestinationView;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;

import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AnalyticEmailDestinationViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements AnalyticEmailDestinationView {

    private final Widget widget;

    @UiField
    TextBox to;
    @UiField
    TextBox cc;
    @UiField
    TextBox bcc;
    @UiField
    SimplePanel subjectTemplatePanel;
    @UiField
    Button testSubjectTemplateBtn;
    @UiField
    SimplePanel bodyTemplatePanel;
    @UiField
    Button testBodyTemplateBtn;
    @UiField
    Button sendTestEmailBtn;

    @Inject
    public AnalyticEmailDestinationViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        testSubjectTemplateBtn.setIcon(SvgImage.TICK);
        testBodyTemplateBtn.setIcon(SvgImage.TICK);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getTo() {
        return this.to.getValue();
    }

    @Override
    public void setTo(final String to) {
        this.to.setValue(to);
    }

    @Override
    public String getCc() {
        return this.cc.getValue();
    }

    @Override
    public void setCc(final String cc) {
        this.cc.setValue(cc);
    }

    @Override
    public String getBcc() {
        return this.bcc.getValue();
    }

    @Override
    public void setBcc(final String bcc) {
        this.bcc.setValue(bcc);
    }

    @Override
    public void setSubjectTemplateEditorView(final View view) {
        if (view != null) {
            subjectTemplatePanel.setWidget(view.asWidget());
        }
    }

    @Override
    public void setBodyTemplateEditorView(final View view) {
        if (view != null) {
            bodyTemplatePanel.setWidget(view.asWidget());
        }
    }

    @UiHandler("to")
    public void onTo(final KeyUpEvent event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("cc")
    public void onCc(final KeyUpEvent event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("bcc")
    public void onBcc(final KeyUpEvent event) {
        getUiHandlers().onDirty();
    }

    @Override
    public Button getSendTestEmailBtn() {
        return sendTestEmailBtn;
    }

    @Override
    public Button getTestSubjectTemplateBtn() {
        return testSubjectTemplateBtn;
    }

    @Override
    public Button getTestBodyTemplateBtn() {
        return testBodyTemplateBtn;
    }

    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, AnalyticEmailDestinationViewImpl> {

    }
}
