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

import stroom.analytics.client.presenter.AnalyticSettingsPresenter.AnalyticSettingsView;
import stroom.analytics.client.presenter.SettingsUiHandlers;
import stroom.analytics.shared.AnalyticRuleLevel;
import stroom.analytics.shared.AnalyticRuleStatus;
import stroom.item.client.SelectionBox;
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

public class AnalyticSettingsViewImpl
        extends ViewWithUiHandlers<SettingsUiHandlers>
        implements AnalyticSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<AnalyticRuleLevel> level;
    @UiField
    SelectionBox<AnalyticRuleStatus> status;
    @UiField
    CustomCheckBox includeRuleDocumentation;
    @UiField
    SimplePanel errorFeed;
    @UiField
    Button setDefaultErrorFeed;

    @Inject
    public AnalyticSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        // A rule need not declare a level or a status, so both offer a blank item to go back to.
        level.setNonSelectString("");
        level.addItems(AnalyticRuleLevel.values());
        status.setNonSelectString("");
        status.addItems(AnalyticRuleStatus.values());

        setDefaultErrorFeed.setTitle("Set as the default error feed for all users");
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        level.focus();
    }

    @Override
    public AnalyticRuleLevel getLevel() {
        return level.getValue();
    }

    @Override
    public void setLevel(final AnalyticRuleLevel level) {
        this.level.setValue(level);
    }

    @Override
    public AnalyticRuleStatus getStatus() {
        return status.getValue();
    }

    @Override
    public void setStatus(final AnalyticRuleStatus status) {
        this.status.setValue(status);
    }

    @Override
    public boolean isIncludeRuleDocumentation() {
        return includeRuleDocumentation.getValue();
    }

    @Override
    public void setIncludeRuleDocumentation(final boolean includeRuleDocumentation) {
        this.includeRuleDocumentation.setValue(includeRuleDocumentation);
    }

    @Override
    public void setErrorFeedView(final View view) {
        this.errorFeed.setWidget(view.asWidget());
    }

    @Override
    public void setSetDefaultVisible(final boolean visible) {
        this.setDefaultErrorFeed.setVisible(visible);
    }

    @UiHandler("level")
    public void onLevelChange(final ValueChangeEvent<AnalyticRuleLevel> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("status")
    public void onStatusChange(final ValueChangeEvent<AnalyticRuleStatus> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("includeRuleDocumentation")
    public void onIncludeRuleDocumentation(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("setDefaultErrorFeed")
    public void onSetDefaultErrorFeed(final ClickEvent event) {
        getUiHandlers().onSetDefaultErrorFeed();
    }

    public interface Binder extends UiBinder<Widget, AnalyticSettingsViewImpl> {

    }
}
