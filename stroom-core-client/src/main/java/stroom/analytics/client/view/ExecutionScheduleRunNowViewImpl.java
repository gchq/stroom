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

import stroom.analytics.client.presenter.ExecutionScheduleRunNowPresenter;
import stroom.analytics.client.presenter.ProcessingStatusUiHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public final class ExecutionScheduleRunNowViewImpl
        extends ViewWithUiHandlers<ProcessingStatusUiHandlers>
        implements ExecutionScheduleRunNowPresenter.ExecutionScheduleRunNowView {

    private final Widget widget;

    @UiField
    FlowPanel label;

    @UiField
    Button applySelectionButton;
    @UiField
    Button applyFilteredButton;

    @Inject
    public ExecutionScheduleRunNowViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        applySelectionButton.setIcon(SvgImage.OK);
        applyFilteredButton.setIcon(SvgImage.GENERATE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }


    @Override
    public void focus() {
        setText("The chosen set of execution schedules will be run now, "
                + "executing according to frequency until up to date.");
    }

    @Override
    public void setText(final String text) {
        label.getElement().setInnerSafeHtml(SafeHtmlUtil.withLineBreaks(text));
    }

    @Override
    public Button getApplySelectionButton() {
        return applySelectionButton;
    }

    @Override
    public Button getApplyFilteredButton() {
        return applyFilteredButton;
    }


    public interface Binder extends UiBinder<Widget, ExecutionScheduleRunNowViewImpl> {

    }
}
