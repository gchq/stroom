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

import stroom.analytics.client.presenter.AnalyticNotificationEditPresenter.AnalyticNotificationEditView;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.item.client.SelectionBox;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AnalyticNotificationEditViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements AnalyticNotificationEditView {

    private final Widget widget;

    @UiField
    CustomCheckBox enabled;
    @UiField
    CustomCheckBox limitNotifications;
    @UiField
    ValueSpinner maxNotifications;
    @UiField
    DurationPicker resumeAfter;
    @UiField
    SelectionBox<NotificationDestinationType> destinationType;
    @UiField
    SimplePanel destinationContainer;

    @Inject
    public AnalyticNotificationEditViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        enabled.setValue(true);
        maxNotifications.setMin(1);
        maxNotifications.setMax(1000);
        maxNotifications.setValue(1);
        destinationType.addItem(NotificationDestinationType.STREAM);
        destinationType.addItem(NotificationDestinationType.EMAIL);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public boolean isEnabled() {
        return enabled.getValue();
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    @Override
    public boolean isLimitNotifications() {
        return this.limitNotifications.getValue();
    }

    @Override
    public void setLimitNotifications(final boolean limitNotifications) {
        this.limitNotifications.setValue(limitNotifications);
    }

    @Override
    public int getMaxNotifications() {
        return this.maxNotifications.getIntValue();
    }

    @Override
    public void setMaxNotifications(final int maxNotifications) {
        this.maxNotifications.setValue(maxNotifications);
    }

    @Override
    public SimpleDuration getResumeAfter() {
        return this.resumeAfter.getValue();
    }

    @Override
    public void setResumeAfter(final SimpleDuration resumeAfter) {
        this.resumeAfter.setValue(resumeAfter);
    }

    @Override
    public NotificationDestinationType getDestinationType() {
        return this.destinationType.getValue();
    }

    @Override
    public void setDestinationType(final NotificationDestinationType destinationType) {
        this.destinationType.setValue(destinationType);
    }

    @Override
    public void setDestinationView(final View view) {
        destinationContainer.setWidget(view.asWidget());
    }

    @UiHandler("enabled")
    public void onEnabled(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("limitNotifications")
    public void onLimitNotifications(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("maxNotifications")
    public void onMaxNotifications(final ValueChangeEvent<Long> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("resumeAfter")
    public void onResumeAfter(final ValueChangeEvent<SimpleDuration> event) {
        getUiHandlers().onDirty();
    }

    @UiHandler("destinationType")
    public void onDestinationType(final ValueChangeEvent<NotificationDestinationType> event) {
        getUiHandlers().onDirty();
    }

    public interface Binder extends UiBinder<Widget, AnalyticNotificationEditViewImpl> {

    }
}
