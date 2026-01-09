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

import stroom.analytics.client.presenter.AbstractProcessingPresenter.AnalyticProcessingView;
import stroom.analytics.client.presenter.AnalyticProcessingUiHandlers;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.item.client.SelectionBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AnalyticProcessingViewImpl
        extends ViewWithUiHandlers<AnalyticProcessingUiHandlers>
        implements AnalyticProcessingView {

    private final Widget widget;

    @UiField
    SelectionBox<AnalyticProcessType> processingType;
    @UiField
    SimplePanel processSettings;

    @Inject
    public AnalyticProcessingViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void addProcessingType(final AnalyticProcessType processingType) {
        this.processingType.addItem(processingType);
    }

    @Override
    public AnalyticProcessType getProcessingType() {
        return this.processingType.getValue();
    }

    @Override
    public void setProcessingType(final AnalyticProcessType analyticProcessType) {
        this.processingType.setValue(analyticProcessType);
    }

    @Override
    public void setProcessSettings(final View view) {
        this.processSettings.setWidget(view.asWidget());
    }

    @UiHandler("processingType")
    public void onProcessingType(final ValueChangeEvent<AnalyticProcessType> event) {
        getUiHandlers().onProcessingTypeChange();
    }

    public interface Binder extends UiBinder<Widget, AnalyticProcessingViewImpl> {

    }
}
