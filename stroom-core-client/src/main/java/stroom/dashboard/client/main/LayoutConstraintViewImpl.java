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

package stroom.dashboard.client.main;

import stroom.dashboard.client.main.LayoutConstraintPresenter.LayoutConstraintView;
import stroom.dashboard.shared.LayoutConstraints;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class LayoutConstraintViewImpl extends ViewImpl
        implements LayoutConstraintView {

    private final Widget widget;

    @UiField
    CustomCheckBox fitWidth;
    @UiField
    CustomCheckBox fitHeight;

    private final EventBus eventBus = new SimpleEventBus();

    @Inject
    public LayoutConstraintViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        fitWidth.setFocus(true);
    }

    @Override
    public boolean isFitWidth() {
        return fitWidth.getValue();
    }

    @Override
    public void setFitWidth(final boolean fitWidth) {
        this.fitWidth.setValue(fitWidth);
    }

    @Override
    public boolean isFitHeight() {
        return fitHeight.getValue();
    }

    @Override
    public void setFitHeight(final boolean fitHeight) {
        this.fitHeight.setValue(fitHeight);
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<LayoutConstraints> handler) {
        return eventBus.addHandler(ValueChangeEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }

    @UiHandler("fitWidth")
    public void onFitWidth(final ValueChangeEvent<Boolean> event) {
        ValueChangeEvent.fire(this, new LayoutConstraints(fitWidth.getValue(), fitHeight.getValue()));
    }

    @UiHandler("fitHeight")
    public void onFitHeight(final ValueChangeEvent<Boolean> event) {
        ValueChangeEvent.fire(this, new LayoutConstraints(fitWidth.getValue(), fitHeight.getValue()));
    }

    public interface Binder extends UiBinder<Widget, LayoutConstraintViewImpl> {

    }
}
