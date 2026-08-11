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

package stroom.planb.client.view;

import stroom.document.client.event.ChangeUiHandlers;
import stroom.planb.client.presenter.TraceSettingsPresenter.TraceSettingsView;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SharedFileStoreSettings;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class TraceSettingsViewImpl
        extends ViewWithUiHandlers<ChangeUiHandlers>
        implements TraceSettingsView {

    private final Widget widget;
    private final GeneralSettingsWidget generalSettingsWidget;
    private final TraceGeneralSettingsWidget traceGeneralSettingsWidget;
    private final RetentionSettingsWidget retentionSettingsWidget;
    private final SharedFileStoreSettingsWidget sharedFileStoreSettingsWidget;

    @UiField
    SettingsGroup generalPanel;
    @UiField
    SettingsGroup retentionPanel;
    @UiField
    SettingsGroup sharedFileStorePanel;

    @Inject
    public TraceSettingsViewImpl(final Binder binder,
                                 final GeneralSettingsWidget generalSettingsWidget,
                                 final TraceGeneralSettingsWidget traceGeneralSettingsWidget,
                                 final RetentionSettingsWidget retentionSettingsWidget,
                                 final SharedFileStoreSettingsWidget sharedFileStoreSettingsWidget) {
        widget = binder.createAndBindUi(this);
        this.generalSettingsWidget = generalSettingsWidget;
        this.traceGeneralSettingsWidget = traceGeneralSettingsWidget;
        this.retentionSettingsWidget = retentionSettingsWidget;
        this.sharedFileStoreSettingsWidget = sharedFileStoreSettingsWidget;

        final FlowPanel generalContent = new FlowPanel();
        generalContent.addStyleName("form");
        final Widget general = generalSettingsWidget.asWidget();
        general.removeStyleName("max");
        generalContent.add(general);
        generalContent.add(traceGeneralSettingsWidget.asWidget());
        generalPanel.add(generalContent);

        retentionPanel.add(retentionSettingsWidget.asWidget());
        sharedFileStorePanel.add(sharedFileStoreSettingsWidget.asWidget());

        // Trace retention deletes by insert time whatever this says, so there is nothing to offer.
        retentionSettingsWidget.setUseStateTimeVisible(false);
    }

    @Override
    public void setUiHandlers(final ChangeUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
        generalSettingsWidget.setUiHandlers(uiHandlers);
        traceGeneralSettingsWidget.setUiHandlers(uiHandlers);
        retentionSettingsWidget.setUiHandlers(uiHandlers);
        sharedFileStoreSettingsWidget.setUiHandlers(uiHandlers);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public Long getMaxStoreSize() {
        return generalSettingsWidget.getMaxStoreSize();
    }

    @Override
    public void setMaxStoreSize(final Long maxStoreSize) {
        generalSettingsWidget.setMaxStoreSize(maxStoreSize);
    }

    @Override
    public Long getMaxSpansPerTrace() {
        return traceGeneralSettingsWidget.getMaxSpansPerTrace();
    }

    @Override
    public void setMaxSpansPerTrace(final Long maxSpansPerTrace) {
        traceGeneralSettingsWidget.setMaxSpansPerTrace(maxSpansPerTrace);
    }

    @Override
    public RetentionSettings getRetention() {
        return retentionSettingsWidget.getRetention();
    }

    @Override
    public void setRetention(final RetentionSettings retention) {
        retentionSettingsWidget.setRetention(retention);
    }

    @Override
    public SharedFileStoreSettings getSharedFileStore() {
        return sharedFileStoreSettingsWidget.getSharedFileStore();
    }

    @Override
    public void setSharedFileStore(final SharedFileStoreSettings sharedFileStore) {
        sharedFileStoreSettingsWidget.setSharedFileStore(sharedFileStore);
    }

    @Override
    public void setSharedFileStoreLocked(final boolean locked) {
        sharedFileStoreSettingsWidget.setSharedFileStoreLocked(locked);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        generalSettingsWidget.onReadOnly(readOnly);
        traceGeneralSettingsWidget.onReadOnly(readOnly);
        retentionSettingsWidget.onReadOnly(readOnly);
        sharedFileStoreSettingsWidget.onReadOnly(readOnly);
    }

    public interface Binder extends UiBinder<Widget, TraceSettingsViewImpl> {

    }
}
