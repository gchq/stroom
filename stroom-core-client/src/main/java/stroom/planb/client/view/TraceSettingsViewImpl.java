/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.planb.shared.BucketGranularity;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.shared.time.SimpleDuration;

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
    private final PublishingSettingsWidget publishingSettingsWidget;
    private final RetentionSettingsWidget retentionSettingsWidget;
    private final SharedFileStoreSettingsWidget sharedFileStoreSettingsWidget;

    @UiField
    SettingsGroup sharedFileStorePanel;
    @UiField
    SettingsGroup publishingPanel;
    @UiField
    SettingsGroup storagePanel;
    @UiField
    SettingsGroup retentionPanel;

    @Inject
    public TraceSettingsViewImpl(final Binder binder,
                                 final GeneralSettingsWidget generalSettingsWidget,
                                 final TraceGeneralSettingsWidget traceGeneralSettingsWidget,
                                 final PublishingSettingsWidget publishingSettingsWidget,
                                 final RetentionSettingsWidget retentionSettingsWidget,
                                 final SharedFileStoreSettingsWidget sharedFileStoreSettingsWidget) {
        widget = binder.createAndBindUi(this);
        this.generalSettingsWidget = generalSettingsWidget;
        this.traceGeneralSettingsWidget = traceGeneralSettingsWidget;
        this.publishingSettingsWidget = publishingSettingsWidget;
        this.retentionSettingsWidget = retentionSettingsWidget;
        this.sharedFileStoreSettingsWidget = sharedFileStoreSettingsWidget;

        sharedFileStorePanel.add(sharedFileStoreSettingsWidget.asWidget());
        publishingPanel.add(publishingSettingsWidget.asWidget());

        // Max store size and the per-trace span limit are both about how much space this store may
        // take, so they share one panel.
        final FlowPanel storageContent = new FlowPanel();
        storageContent.addStyleName("form");
        final Widget general = generalSettingsWidget.asWidget();
        general.removeStyleName("max");
        storageContent.add(general);
        storageContent.add(traceGeneralSettingsWidget.asWidget());
        storagePanel.add(storageContent);

        retentionPanel.add(retentionSettingsWidget.asWidget());

        // Trace retention deletes by insert time whatever this says, so there is nothing to offer.
        retentionSettingsWidget.setUseStateTimeVisible(false);
    }

    @Override
    public void setUiHandlers(final ChangeUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
        generalSettingsWidget.setUiHandlers(uiHandlers);
        traceGeneralSettingsWidget.setUiHandlers(uiHandlers);
        publishingSettingsWidget.setUiHandlers(uiHandlers);
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
    public BucketGranularity getGranularity() {
        return publishingSettingsWidget.getGranularity();
    }

    @Override
    public void setGranularity(final BucketGranularity granularity) {
        publishingSettingsWidget.setGranularity(granularity);
    }

    @Override
    public SimpleDuration getCompletionGrace() {
        return publishingSettingsWidget.getCompletionGrace();
    }

    @Override
    public void setCompletionGrace(final SimpleDuration completionGrace) {
        publishingSettingsWidget.setCompletionGrace(completionGrace);
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
        publishingSettingsWidget.onReadOnly(readOnly);
        retentionSettingsWidget.onReadOnly(readOnly);
        sharedFileStoreSettingsWidget.onReadOnly(readOnly);
    }

    public interface Binder extends UiBinder<Widget, TraceSettingsViewImpl> {

    }
}
