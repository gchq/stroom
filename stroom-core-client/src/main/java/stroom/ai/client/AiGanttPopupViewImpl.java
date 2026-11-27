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
import stroom.widget.tab.client.view.GlobalResizeObserver;
import stroom.widget.util.client.ElementUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.function.IntConsumer;

/// The chart dialog's view: a panel filling the dialog, scrolled when the chart is taller, watched for its size so
/// the chart is drawn again at its width (and no longer watched once the dialog is closed).
public class AiGanttPopupViewImpl extends ViewImpl implements AiGanttPopupView {

    // The panel's padding on each side (ask-stroom-ai.css, .ai-gantt-dialog).
    private static final int PADDING = 12;

    private final FlowPanel panel = new FlowPanel();
    private IntConsumer onResize;

    /// Makes the dialog's panel.
    public AiGanttPopupViewImpl() {
        panel.setStyleName("max ai-gantt-dialog");
        panel.addAttachHandler(event -> {
            if (event.isAttached()) {
                GlobalResizeObserver.addListener(panel.getElement(), element -> {
                    if (onResize != null) {
                        onResize.accept(getChartWidth());
                    }
                });
            } else {
                GlobalResizeObserver.removeListener(panel.getElement());
            }
        });
    }

    @Override
    public Widget asWidget() {
        return panel;
    }

    @Override
    public void setChart(final String chartHtml) {
        // A chart's element of escaped SVG and constant buttons only (AiMermaidCharts).
        panel.getElement().setInnerHTML(chartHtml);
    }

    @Override
    public Element getChart() {
        return ElementUtil.findChild(panel.getElement(), element ->
                element.hasAttribute(AiMermaidCharts.SOURCE_ATTRIBUTE));
    }

    @Override
    public int getChartWidth() {
        return panel.getElement().getClientWidth() - 2 * PADDING;
    }

    @Override
    public Widget getContainer() {
        return panel;
    }

    @Override
    public void setOnResize(final IntConsumer onResize) {
        this.onResize = onResize;
    }
}
