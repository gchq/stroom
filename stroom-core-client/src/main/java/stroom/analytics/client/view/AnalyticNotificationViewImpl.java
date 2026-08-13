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

package stroom.analytics.client.view;

import stroom.analytics.client.presenter.AbstractNotificationPresenter.AnalyticNotificationView;
import stroom.analytics.client.presenter.AnalyticNotificationUiHandlers;
import stroom.widget.button.client.Button;
import stroom.widget.form.client.FormGroup;
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

public class AnalyticNotificationViewImpl
        extends ViewWithUiHandlers<AnalyticNotificationUiHandlers>
        implements AnalyticNotificationView {

    private final Widget widget;

    @UiField
    SimplePanel errorFeed;
    @UiField
    Button setDefaultErrorFeed;
    @UiField
    SimplePanel table;
    @UiField
    FormGroup includeRuleDocumentationFormGroup;
    @UiField
    CustomCheckBox includeRuleDocumentation;

    @Inject
    public AnalyticNotificationViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setDefaultErrorFeed.setTitle("Set as the default error feed for all users");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setErrorFeedView(final View view) {
        this.errorFeed.setWidget(view.asWidget());
    }

    @Override
    public void setSetDefaultVisible(final boolean visible) {
        this.setDefaultErrorFeed.setVisible(visible);
    }

    @UiHandler("setDefaultErrorFeed")
    public void onSetDefaultErrorFeed(final ClickEvent event) {
        getUiHandlers().onSetDefaultErrorFeed();
    }

    @Override
    public void setIncludeRuleDocumentationVisible(final boolean visible) {
        includeRuleDocumentationFormGroup.setVisible(visible);
    }

    @Override
    public boolean isIncludeRuleDocumentation() {
        return this.includeRuleDocumentation.getValue();
    }

    @Override
    public void setIncludeRuleDocumentation(final boolean includeRuleDocumentation) {
        this.includeRuleDocumentation.setValue(includeRuleDocumentation);
    }

    @Override
    public void setTable(final View view) {
        this.table.setWidget(view.asWidget());
    }

    @UiHandler("includeRuleDocumentation")
    public void onIncludeRuleDocumentation(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, AnalyticNotificationViewImpl> {

    }
}
