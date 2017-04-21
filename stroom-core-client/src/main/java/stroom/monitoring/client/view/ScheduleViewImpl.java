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

package stroom.monitoring.client.view;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.monitoring.client.presenter.SchedulePresenter.ScheduleView;

public class ScheduleViewImpl extends ViewImpl implements ScheduleView {
    private final Label lblScheduleType = new Label("", false);
    private final TextBox txtScheduleString = new TextBox();
    private final Label lblLastExecutedTime = new Label("", false);
    private final Label lblNextScheduledTime = new Label("", false);
    private final Button btnCalculate = new Button("Calculate");

    private final Widget widget;

    public ScheduleViewImpl() {
        final Label lbl1 = lblScheduleType;
        final Label lbl2 = new Label("Last Executed:       ", false);
        final Label lbl3 = new Label("Next Scheduled Time: ", false);

        final Grid grid = new Grid(4, 2);
        grid.setSize("100%", "100%");
        grid.setWidget(0, 0, lbl1);
        grid.setWidget(0, 1, txtScheduleString);
        grid.setWidget(1, 0, lbl2);
        grid.setWidget(1, 1, lblLastExecutedTime);
        grid.setWidget(2, 0, lbl3);
        grid.setWidget(2, 1, lblNextScheduledTime);
        grid.setWidget(3, 0, btnCalculate);

        lbl2.getElement().getStyle().setPaddingRight(5, Unit.PX);
        lbl2.getElement().getStyle().setPaddingBottom(5, Unit.PX);
        lbl3.getElement().getStyle().setPaddingRight(5, Unit.PX);
        lbl3.getElement().getStyle().setPaddingBottom(5, Unit.PX);

        lblLastExecutedTime.getElement().getStyle().setPaddingBottom(5, Unit.PX);
        lblNextScheduledTime.getElement().getStyle().setPaddingBottom(5, Unit.PX);

        widget = grid;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public HasText getLastExecutedTime() {
        return lblLastExecutedTime;
    }

    @Override
    public HasText getNextScheduledTime() {
        return lblNextScheduledTime;
    }

    @Override
    public HasText getScheduledType() {
        return lblNextScheduledTime;
    }

    @Override
    public HasText getScheduledString() {
        return txtScheduleString;
    }

    @Override
    public HasClickHandlers getCalculateButton() {
        return btnCalculate;
    }
}
