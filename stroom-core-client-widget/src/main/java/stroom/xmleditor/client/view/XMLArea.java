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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.HasMouseDownHandlers;
import com.google.gwt.event.dom.client.HasScrollHandlers;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.SimplePanel;

import stroom.widget.contextmenu.client.event.ContextMenuEvent;
import stroom.widget.contextmenu.client.event.HasContextMenuHandlers;

public abstract class XMLArea extends Composite
        implements HasContextMenuHandlers, HasMouseDownHandlers, HasKeyDownHandlers, HasScrollHandlers {
    private static final String STYLE_NAME = "xmlArea";
    private static final String STYLE_NAME_CONTENT = "xmlArea-Content";
    private static final int LINE_HEIGHT = 14;

    private Element container;
    private Element content;

    private static final int PLAY_BUTTON_HEIGHT = 69;
    private int controlsHeight = PLAY_BUTTON_HEIGHT;

    private int scrollTop;

    @Override
    public void onBrowserEvent(final Event event) {
        try {
            final int eventType = DOM.eventGetType(event);
            if (event.getButton() == Event.BUTTON_RIGHT
                    && (eventType == Event.ONMOUSEDOWN || eventType == Event.ONMOUSEUP || eventType == Event.ONCLICK
                            || eventType == Event.ONCONTEXTMENU || eventType == Event.ONDBLCLICK)) {
                event.stopPropagation();
                event.preventDefault();

                fireContextMenuEvent(event);

            } else {
                super.onBrowserEvent(event);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    public void scrollToLine(final int line) {
        if (container != null) {
            final int pos = line * LINE_HEIGHT;
            container.setScrollLeft(0);
            container.setScrollTop(pos);
        }
    }

    public void scrollHighlightIntoView() {
        try {
            if (container != null) {
                final NodeList<com.google.gwt.dom.client.Element> domElem = container.getElementsByTagName("hl");

                if (domElem != null && domElem.getLength() > 0) {
                    // Get the highlight rect.
                    int left = Integer.MAX_VALUE;
                    int right = 0;
                    int top = Integer.MAX_VALUE;
                    int bottom = 0;

                    // Establish a bounding rect for the highlight area.
                    for (int i = 0; i < domElem.getLength(); i++) {
                        final Element element = domElem.getItem(i).cast();
                        if (element.getOffsetLeft() < left) {
                            left = element.getOffsetLeft();
                        }
                        if (element.getOffsetLeft() + element.getOffsetWidth() > right) {
                            right = element.getOffsetLeft() + element.getOffsetWidth();
                        }
                        if (element.getOffsetTop() < top) {
                            top = element.getOffsetTop();
                        }
                        if (element.getOffsetTop() + element.getOffsetHeight() > bottom) {
                            bottom = element.getOffsetTop() + element.getOffsetHeight();
                        }
                    }

                    // Get the body rect.
                    final int bodyLeft = container.getScrollLeft();
                    final int bodyRight = bodyLeft + container.getClientWidth();
                    final int bodyTop = container.getScrollTop();
                    final int bodyBottom = bodyTop + container.getClientHeight();

                    // Position the body scroll so that as much of the highlight
                    // is shown as possible.
                    if (bodyLeft > left) {
                        container.setScrollLeft(left);
                    } else if (bodyRight < right) {
                        final int width = right - left;
                        final int bodyWidth = bodyRight - bodyLeft;

                        if (width > bodyWidth) {
                            // The highlight section is too big to fit so show
                            // the leftmost part.
                            container.setScrollLeft(left);
                        } else {
                            // It will fit so show the rightmost part.
                            container.setScrollLeft(right - bodyWidth);
                        }
                    }

                    if (bodyTop > top) {
                        container.setScrollTop(top);
                    } else if (bodyBottom < bottom) {
                        final int height = bottom - top;
                        final int bodyHeight = bodyBottom - bodyTop;

                        if (height > bodyHeight) {
                            // The highlight section is too big to fit so show
                            // the topmost part.
                            container.setScrollTop(top);
                        } else {
                            // It will fit so show the bottommost part.
                            container.setScrollTop(bottom - bodyHeight);
                        }
                    }
                }
            }
        } catch (final Exception e) {
            // Fails when source is not visible. This is not important so do
            // nothing.
        }
    }

    public int getScrollTop() {
        return container.getScrollTop();
    }

    public int getScrollHeight() {
        return container.getScrollHeight();
    }

    protected Element initBody(final boolean editable) {
        final SimplePanel panel = new SimplePanel();
        initWidget(panel);

        content = Document.get().createDivElement().cast();
        content.setClassName(STYLE_NAME_CONTENT);

        if (editable) {
            content.setAttribute("contenteditable", "true");
            content.getStyle().setCursor(Cursor.TEXT);
        }

        container = panel.getElement();
        container.setClassName(STYLE_NAME);
        container.setAttribute("oncontextmenu", "return false;");
        container.appendChild(content);

        handleScroll(container);

        return container;
    }

    @Override
    protected void onAttach() {
        super.onAttach();
        container.setScrollTop(scrollTop);
    }

    @Override
    protected void onDetach() {
        scrollTop = container.getScrollTop();
        super.onDetach();
    }

    public void setControlsVisible(final boolean visible) {
        if (visible) {
            container.getStyle().setPaddingBottom(controlsHeight, Unit.PX);
        } else {
            container.getStyle().setPaddingBottom(0, Unit.PX);
        }
    }

    public void setControlsHeight(final int controlsHeight) {
        this.controlsHeight = controlsHeight;
    }

    private void handleScroll(final Element element) {
        DOM.sinkEvents(element, Event.ONSCROLL);
        DOM.setEventListener(element, new EventListener() {
            @Override
            public void onBrowserEvent(final Event event) {
                final int eventType = DOM.eventGetType(event);
                if (eventType == Event.ONSCROLL) {
                    ScrollEvent.fireNativeEvent(event, XMLArea.this);
                }
            }
        });
    }

    public Element getBody() {
        return container;
    }

    private void fireContextMenuEvent(final Event event) {
        ContextMenuEvent.fire(this, event.getClientX(), event.getClientY());
    }

    public void setHTML(final String html) {
        if (content != null) {
            content.setInnerHTML(html);
        }
    }

    public String getHTML() {
        if (content != null) {
            return content.getInnerHTML();
        }

        return null;
    }

    @Override
    public HandlerRegistration addContextMenuHandler(final ContextMenuEvent.Handler handler) {
        return addHandler(handler, ContextMenuEvent.getType());
    }

    @Override
    public HandlerRegistration addMouseDownHandler(final MouseDownHandler handler) {
        return addHandler(handler, MouseDownEvent.getType());
    }

    @Override
    public HandlerRegistration addKeyDownHandler(final KeyDownHandler handler) {
        return addHandler(handler, KeyDownEvent.getType());
    }

    @Override
    public HandlerRegistration addScrollHandler(final ScrollHandler handler) {
        return addHandler(handler, ScrollEvent.getType());
    }
}
