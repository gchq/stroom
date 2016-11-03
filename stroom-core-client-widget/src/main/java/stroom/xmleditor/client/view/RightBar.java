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

package stroom.xmleditor.client.view;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;
import com.google.gwt.user.client.ui.SimplePanel;

import stroom.util.shared.Indicator;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.xmleditor.client.presenter.Option;

public class RightBar extends Composite {
    public interface Style extends CssResource {
        String rightBar();

        String summaryContainer();

        String summary();

        String marker();

        String info();

        String warning();

        String error();

        String fatal();
    }

    public interface Resources extends ClientBundle {
        @Source("rightbar.css")
        Style style();
    }

    private static volatile Resources resources;

    private static final int SUMMARY_CONTAINER_HEIGHT = 13;
    private static final int OVERVIEW_WIDTH = 13;
    private static final int LINE_HEIGHT = 14;

    private static final IndicatorPopup indicatorPopup = new IndicatorPopup();
    private Indicators indicators;
    private int width;

    private XMLArea xmlArea;

    public RightBar() {
        sinkEvents(Event.ONMOUSEDOWN);

        if (resources == null) {
            synchronized (LeftBar.class) {
                if (resources == null) {
                    resources = GWT.create(Resources.class);
                    resources.style().ensureInjected();
                }
            }
        }

        final SimplePanel simplePanel = new SimplePanel();
        simplePanel.setStyleName(resources.style().rightBar());

        initWidget(simplePanel);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final int eventType = DOM.eventGetType(event);
        final Element target = DOM.eventGetTarget(event);

        event.stopPropagation();
        event.preventDefault();

        if (eventType == Event.ONMOUSEDOWN && (event.getButton() & NativeEvent.BUTTON_LEFT) != 0
                && indicators != null) {
            final String className = target.getClassName();
            if (className != null) {
                if (className.toUpperCase().contains(resources.style().marker().toUpperCase())) {
                    // This is a marker box.
                    final String lineNo = target.getAttribute("lineno");
                    if (lineNo != null) {
                        final int line = Integer.parseInt(lineNo);
                        xmlArea.scrollToLine(line);
                    }

                } else if (className.toUpperCase().contains(resources.style().summary().toUpperCase())) {
                    // This is a summary box.
                    final Element parent = target.getParentElement().cast();
                    final int x = parent.getAbsoluteLeft();
                    final int y = parent.getAbsoluteTop();

                    showIndicatorPopup(x, y, true, indicators.getSummaryHTML());
                }
            }
        }
    }

    /**
     * Redraws the overview bar. This is called when indicators are
     * added/removed, when indicators are set to be displayed and when the
     * widget is resized.
     */
    public void render(final int startLineNo, final int scrollTop, final int scrollHeight,
            final Option lineNumbersOption, final Option indicatorsOption, final Indicators indicators) {
        this.indicators = indicators;
        final StringBuilder sb = new StringBuilder();
        int width = 0;

        if (indicatorsOption.isOk()) {
            if (indicators != null) {
                final Severity maxSeverity = indicators.getMaxSeverity();
                if (maxSeverity != null) {
                    // Add the top summary marker.
                    sb.append("<div class=\"");
                    sb.append(resources.style().summaryContainer());
                    sb.append("\">");

                    sb.append("<div class=\"");
                    sb.append(resources.style().summary());
                    sb.append(" ");
                    switch (maxSeverity) {
                    case FATAL_ERROR:
                        sb.append(resources.style().fatal());
                        break;
                    case ERROR:
                        sb.append(resources.style().error());
                        break;
                    case WARNING:
                        sb.append(resources.style().warning());
                        break;
                    default:
                        sb.append(resources.style().info());
                        break;
                    }
                    sb.append("\">");
                    sb.append("</div>");

                    sb.append("</div>");
                }

                final int minMarkerBarTop = getMinMarkerBarTop();
                final int maxMarkerBarTop = getMaxMarkerBarTop();
                final double scrollBarTop = getScrollBarTop();
                final double scrollBarHeight = getScrollBarHeight();
                final int maxLineNo = scrollHeight / LINE_HEIGHT;

                if (maxLineNo > 0 && scrollBarHeight > 0) {
                    final double increment = scrollBarHeight / maxLineNo;

                    int lastTop = -1;
                    Severity lastSeverity = null;

                    for (int lineNo : indicators.getLineNumbers()) {
                        if (lineNo <= maxLineNo) {
                            final Indicator indicator = indicators.getIndicator(lineNo);
                            final Severity severity = indicator.getMaxSeverity();

                            if (severity != null) {
                                // Get the top pixel position for the marker.
                                if (lineNo < 1) {
                                    lineNo = 1;
                                }
                                lineNo--;

                                int top = (int) (increment * lineNo);
                                top += scrollBarTop;
                                if (top < minMarkerBarTop) {
                                    top = minMarkerBarTop;
                                } else if (top > maxMarkerBarTop) {
                                    top = maxMarkerBarTop;
                                }

                                // Don't add multiple markers at the same
                                // position unless we are adding an error on top
                                // of a warning.
                                if (lastTop != top || (lastSeverity != null && severity.greaterThan(lastSeverity))) {
                                    sb.append("<div lineno=\"");
                                    sb.append(lineNo);
                                    sb.append("\" class=\"");
                                    sb.append(resources.style().marker());
                                    sb.append(" ");
                                    switch (severity) {
                                    case FATAL_ERROR:
                                        sb.append(resources.style().fatal());
                                        break;
                                    case ERROR:
                                        sb.append(resources.style().error());
                                        break;
                                    case WARNING:
                                        sb.append(resources.style().warning());
                                        break;
                                    default:
                                        sb.append(resources.style().info());
                                        break;
                                    }
                                    sb.append("\" style=\"top:");
                                    sb.append(top);
                                    sb.append("px;\">");
                                    sb.append("</div>");

                                    lastTop = top;
                                    lastSeverity = severity;
                                }
                            }
                        }
                    }
                }
            }

            width = OVERVIEW_WIDTH;
        }

        this.width = width;
        getElement().setInnerHTML(sb.toString());
    }

    private int getMinMarkerBarTop() {
        final int scrollBarTop = getScrollBarTop();

        int top = SUMMARY_CONTAINER_HEIGHT;
        top += 1;

        if (top < scrollBarTop) {
            return scrollBarTop;
        }

        return top;
    }

    private int getMaxMarkerBarTop() {
        return getScrollBarTop() + getScrollBarHeight();
    }

    private int getScrollBarTop() {
        if (isChrome()) {
            return 0;
        }

        return ScrollbarMetrics.getHorizontalScrollBarWidth();
    }

    private int getScrollBarHeight() {
        final int scrollBarWidth = ScrollbarMetrics.getHorizontalScrollBarWidth();

        int height = getOffsetHeight();
        height -= scrollBarWidth;
        // If we aren't using a chrome browser then there will be scroll bar
        // buttons that we need to remove.
        if (!isChrome()) {
            height -= (2 * scrollBarWidth);
        }
        return height;
    }

    private boolean isChrome() {
        return Window.Navigator.getUserAgent().toLowerCase().contains("chrome");
    }

    /**
     * Shows text associated with an indicator in a popup.
     */
    private void showIndicatorPopup(final int xPos, final int yPos, final boolean showLeft, final String html) {
        indicatorPopup.setHTML(html);
        indicatorPopup.setPopupPositionAndShow(new PositionCallback() {
            @Override
            public void setPosition(final int offsetWidth, final int offsetHeight) {
                int x = xPos;
                final int minX = 0;
                final int maxX = Window.getClientWidth() - offsetWidth;
                int y = yPos;
                final int minY = 0;
                final int maxY = Window.getClientHeight() - offsetHeight;

                if (showLeft) {
                    x -= offsetWidth;
                }

                if (x < minX) {
                    x = minX;
                } else if (x > maxX) {
                    x = maxX;
                }

                if (y < minY) {
                    y = minY;
                } else if (y > maxY) {
                    y = maxY;
                }

                indicatorPopup.setPopupPosition(x, y);
            }
        });
    }

    public int getWidth() {
        return width;
    }

    public void setXmlArea(final XMLArea xmlArea) {
        this.xmlArea = xmlArea;
    }
}
