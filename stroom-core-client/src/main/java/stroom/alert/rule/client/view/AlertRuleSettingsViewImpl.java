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

package stroom.alert.rule.client.view;

import stroom.alert.rule.client.presenter.AlertRuleSettingsPresenter.AlertRuleSettingsView;
import stroom.alert.rule.shared.AlertRuleType;
import stroom.alert.rule.shared.QueryLanguageVersion;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.item.client.ItemListBox;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.dom.client.InputEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AlertRuleSettingsViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements AlertRuleSettingsView, ReadOnlyChangeHandler {

    private final Widget widget;

    @UiField
    TextArea description;
    @UiField
    ItemListBox<QueryLanguageVersion> languageVersion;
    @UiField
    SimplePanel query;
    @UiField
    CustomCheckBox enabled;
    @UiField
    ItemListBox<AlertRuleType> alertRuleType;
    @UiField
    TextBox timeField;
    @UiField
    TextBox executionDelay;
    @UiField
    TextBox executionFrequency;
    @UiField
    SimplePanel destinationFeed;

    @Inject
    public AlertRuleSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        languageVersion.addItem(QueryLanguageVersion.STROOM_QL_VERSION_0_1);
        languageVersion.addItem(QueryLanguageVersion.SIGMA);

        description.addDomHandler(e -> getUiHandlers().onDirty(), InputEvent.getType());

        alertRuleType.addItem(AlertRuleType.EVENT);
        alertRuleType.addItem(AlertRuleType.THRESHOLD);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getDescription() {
        return description.getValue().trim();
    }

    @Override
    public void setDescription(final String description) {
        this.description.setValue(description);
    }

    @Override
    public QueryLanguageVersion getLanguageVersion() {
        return languageVersion.getSelectedItem();
    }

    public void setLanguageVersion(final QueryLanguageVersion languageVersion) {
        if (languageVersion == null) {
            this.languageVersion.setSelectedItem(QueryLanguageVersion.STROOM_QL_VERSION_0_1);
        } else {
            this.languageVersion.setSelectedItem(languageVersion);
        }
    }

    @Override
    public void setQueryWidget(final Widget widget) {
        this.query.setWidget(widget);
    }

    @Override
    public boolean isEnabled() {
        return this.enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }


    @Override
    public AlertRuleType getAlertRuleType() {
        return this.alertRuleType.getSelectedItem();
    }

    @Override
    public void setAlertRuleType(final AlertRuleType alertRuleType) {
        this.alertRuleType.setSelectedItem(alertRuleType);
    }

    @Override
    public String getTimeField() {
        return this.timeField.getValue();
    }

    @Override
    public void setTimeField(final String timeField) {
        this.timeField.setValue(timeField);
    }

    @Override
    public String getExecutionDelay() {
        return this.executionDelay.getValue();
    }

    @Override
    public void setExecutionDelay(final String executionDelay) {
        this.executionDelay.setValue(executionDelay);
    }

    @Override
    public String getExecutionFrequency() {
        return this.executionFrequency.getValue();
    }

    @Override
    public void setExecutionFrequency(final String executionFrequency) {
        this.executionFrequency.setValue(executionFrequency);
    }

    @Override
    public void setDestinationFeedView(final View view) {
        this.destinationFeed.setWidget(view.asWidget());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        description.setEnabled(!readOnly);
    }

    public interface Binder extends UiBinder<Widget, AlertRuleSettingsViewImpl> {

    }
}
