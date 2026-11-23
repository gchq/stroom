/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AiGanttPopupPresenter.AiGanttPopupView;
import stroom.ai.shared.MermaidGantt;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.IntConsumer;

/// A chart from the chat shown larger, in a dialog of its own (Stroom's popup, as the chat's other dialogs are):
/// drawn at the dialog's width, again as the dialog is resized, starting at the span the chat's chart shows, and
/// zoomed and reset as it is in the chat ([AiGanttInteraction]). Closing it leaves the chat's chart as it was.
public class AiGanttPopupPresenter extends MyPresenterWidget<AiGanttPopupView> {

    private static final int INITIAL_WIDTH = 1200;
    private static final int INITIAL_HEIGHT = 700;

    /// @param eventBus The event bus.
    /// @param view     The dialog's view.
    @Inject
    public AiGanttPopupPresenter(final EventBus eventBus, final AiGanttPopupView view) {
        super(eventBus, view);
        view.setOnResize(this::onResize);
    }

    @Override
    protected void onBind() {
        super.onBind();
        // Nothing to maximise in the dialog itself.
        for (final HandlerRegistration registration : new AiGanttInteraction(chart -> {
        }).bind(getView().getContainer())) {
            registerHandler(registration);
        }
    }

    /// Shows a chart from the chat in the dialog.
    ///
    /// @param chatChart The chart's element in the chat (of [AiMermaidCharts#CHART_CLASS]).
    public void show(final Element chatChart) {
        final String text = chatChart.getAttribute(AiMermaidCharts.SOURCE_ATTRIBUTE);
        final MermaidGantt gantt = AiMermaidCharts.parse(text);
        if (gantt == null) {
            return;
        }
        getView().setChart(AiMermaidCharts.chart(text, gantt, AiGanttInteraction.viewOf(chatChart),
                AiGanttChart.WIDTH, false));
        final String caption = gantt.getTitle() == null || gantt.getTitle().isEmpty()
                ? "Timeline"
                : gantt.getTitle();
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(INITIAL_WIDTH, INITIAL_HEIGHT))
                .caption(caption)
                .onShow(event -> onResize(getView().getChartWidth()))
                .fire();
    }

    // Draws the chart again at the dialog's width, keeping its span.
    private void onResize(final int width) {
        final Element chart = getView().getChart();
        if (chart == null) {
            return;
        }
        final int drawnWidth = Math.max(AiGanttChart.MIN_WIDTH, width);
        if (drawnWidth != AiGanttInteraction.widthOf(chart)) {
            chart.setAttribute(AiMermaidCharts.WIDTH_ATTRIBUTE, String.valueOf(drawnWidth));
            AiGanttInteraction.draw(chart, AiGanttInteraction.viewOf(chart), drawnWidth);
        }
    }


    // --------------------------------------------------------------------------------


    /// The dialog's view: the chart, scrolled when it is taller than the dialog.
    public interface AiGanttPopupView extends View {

        /// @param chartHtml The chart's element, as [AiMermaidCharts#chart] writes it.
        void setChart(String chartHtml);

        /// @return The chart's element, or null if none is shown.
        Element getChart();

        /// @return The width a chart has to be drawn in, in pixels.
        int getChartWidth();

        /// @return The widget the chart is in, for its handlers.
        Widget getContainer();

        /// @param onResize Called with [#getChartWidth] as the dialog is resized.
        void setOnResize(IntConsumer onResize);
    }
}
