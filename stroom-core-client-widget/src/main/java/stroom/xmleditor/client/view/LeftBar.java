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
import com.google.gwt.resources.client.ImageResource;
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

public class LeftBar extends Composite {
    public interface Style extends CssResource {
        String leftBar();

        String container();

        String indicators();

        String lineNumbers();

        String bottomLeft();

        String info();

        String warning();

        String error();

        String fatal();
    }

    public interface Resources extends ClientBundle {
        @Source("leftbar.css")
        Style style();

        ImageResource info();

        ImageResource warning();

        ImageResource error();

        ImageResource fatal();
    }

    private static volatile Resources resources;

    private static final String IND_TAG = "ind";
    private static final int CHAR_WIDTH = 7;
    private static final int LINE_HEIGHT = 14;
    private static final String NEW_LINE = "<br/>";

    private static final IndicatorPopup indicatorPopup = new IndicatorPopup();
    private Indicators indicators;
    private int width;

    public LeftBar() {
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
        simplePanel.setStyleName(resources.style().leftBar());

        initWidget(simplePanel);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        final int eventType = DOM.eventGetType(event);
        final Element target = DOM.eventGetTarget(event);
        final String tagName = target.getTagName();

        event.stopPropagation();
        event.preventDefault();

        if (eventType == Event.ONMOUSEDOWN && (event.getButton() & NativeEvent.BUTTON_LEFT) != 0) {
            if (IND_TAG.equalsIgnoreCase(tagName)) {
                final String lineNo = target.getAttribute("lineno");
                if (lineNo != null && indicators != null) {
                    try {
                        final int line = Integer.parseInt(lineNo);
                        final Indicator indicator = indicators.getIndicator(line);

                        final int x = target.getAbsoluteLeft() + 15;
                        final int y = target.getAbsoluteTop();

                        showIndicatorPopup(x, y, false, indicator.getHTML());

                        // IndicatorEvent.fire(this, target, line);
                    } catch (final NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Creates the HTML for a set of divs to display indicators and line numbers
     * to the left of the content depending on the current settings.
     */
    public void render(final int startLineNo, final int scrollTop, final int scrollHeight,
            final Option lineNumbersOption, final Option indicatorsOption, final Indicators indicators) {
        this.indicators = indicators;

        final StringBuilder sb = new StringBuilder();
        final int top = -(scrollTop % LINE_HEIGHT);
        int width = 0;

        if (indicatorsOption.isOk() || lineNumbersOption.isOk()) {
            final int offsetHeight = getOffsetHeight();
            final int minLineNo = startLineNo + (scrollTop / LINE_HEIGHT);
            final int maxLineNo = minLineNo + (offsetHeight / LINE_HEIGHT) + 2;

            // Add a div that is positioned to account for the current scroll
            // position.
            sb.append("<div class=\"");
            sb.append(resources.style().container());
            sb.append("\" style=\"top:");
            sb.append(top);
            sb.append("px;\"");
            sb.append(">");

            if (indicatorsOption.isOk()) {
                renderIndicators(sb, width, minLineNo, maxLineNo, indicators);
                width += 12;
            }
            if (lineNumbersOption.isOk()) {
                width += renderLineNumbers(sb, width, minLineNo, maxLineNo);
            }

            // Add on 1 pixel for the border.
            width += 1;

            sb.append("</div>");

            // Add a final div to obscure the bottom left.
            sb.append("<div class=\"");
            sb.append(resources.style().bottomLeft());
            sb.append("\"></div>");
        }

        this.width = width;
        getElement().setInnerHTML(sb.toString());
    }

    private void renderIndicators(final StringBuilder sb, final int left, final int minLineNo, final int maxLineNo,
            final Indicators indicators) {
        sb.append("<div class=\"");
        sb.append(resources.style().indicators());
        sb.append("\"");
        if (left > 0) {
            sb.append(" style=\"left:");
            sb.append(left);
            sb.append("px;\"");
        }
        sb.append(">");
        if (indicators != null) {
            final Severity maxSeverity = indicators.getMaxSeverity();
            if (maxSeverity != null) {
                for (int lineNo = minLineNo; lineNo < maxLineNo; lineNo++) {
                    final Indicator indicator = indicators.getIndicator(lineNo);
                    if (indicator != null) {
                        sb.append("<ind lineno=\"");
                        sb.append(lineNo);
                        sb.append("\" class=\"");

                        final Severity severity = indicator.getMaxSeverity();
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

                        sb.append("\"></ind>");
                    }
                    sb.append(NEW_LINE);
                }
            }
        }
        sb.append("</div>");
    }

    private int renderLineNumbers(final StringBuilder sb, final int left, final int minLineNo, final int maxLineNo) {
        final int chars = Integer.toString(maxLineNo).length();

        // Calculate the width as the number of chars times the width of each
        // char.
        int width = chars * CHAR_WIDTH;

        // Add on 2 pixels padding each side.
        width += 4;

        sb.append("<div class=\"");
        sb.append(resources.style().lineNumbers());
        sb.append("\"");
        sb.append(" style=\"left:");
        sb.append(left);
        sb.append("px;width:");
        sb.append(width);
        sb.append("px;\"");
        sb.append(">");
        for (int lineNo = minLineNo; lineNo < maxLineNo; lineNo++) {
            sb.append(lineNo);
            sb.append(NEW_LINE);
        }
        sb.append("</div>");

        return width;
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
}
