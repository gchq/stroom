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

package stroom.widget.button.client;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Display;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.ButtonBase;
import stroom.widget.util.client.ResourceCache;

public class SVGButton extends ButtonBase implements SVGButtonView {
    /**
     * If <code>true</code>, this widget is capturing with the mouse held down.
     */
    private boolean isCapturing;

    /**
     * If <code>true</code>, this widget has focus with the space bar down.
     */
    private boolean isFocusing;

    /**
     * Used to decide whether to allow clicks to propagate up to the superclass
     * or container elements.
     */
    private boolean allowClick;

    public static SVGButton create(final String icon, final String title, final boolean enabled) {
        final SVGButton button = new SVGButton();
        button.setIcon(icon);
        button.setTitle(title);
        button.setEnabled(enabled);
        return button;
    }

    public static SVGButton create(final SVGIcon preset) {
        final SVGButton button = new SVGButton();
        button.setIcon(preset.getUrl());
        button.setTitle(preset.getTitle());
        button.setEnabled(preset.isEnabled());
        return button;
    }

    public SVGButton() {
        super(Document.get().createDivElement());
        sinkEvents(Event.ONCLICK | Event.MOUSEEVENTS | Event.FOCUSEVENTS | Event.KEYEVENTS);
        getElement().getStyle().setDisplay(Display.INLINE_BLOCK);

//        Image image = new Image();
//        image.addLoadHandler(event -> {
//            final NodeList<Element> elements =  image.getElement().getElementsByTagName("svg");
//            if (elements != null && elements.getLength() > 0) {
//                final Element svg = elements.getItem(0);
//                copyAttribute(image.getElement(), svg, "id");
//                copyAttribute(image.getElement(), svg, "class");
//                svg.setAttribute("class", svg.getAttribute("class") + " replaced-svg");
//                image.getElement().getParentElement().replaceChild(image.getElement(), svg);
//            }
//        });


// 	<object id="logo" style="position:absolute;top:0px;left:0px;width:146px;height:35px" type="image/svg+xml" data="images/logo.svg" />

        getElement().setClassName("svg-button");
//        getElement().setAttribute("type", "image/svg+xml");
    }

    private void copyAttribute(final Element from, final Element to, final String key) {
        final String value = from.getAttribute(key);
        if (value != null) {
            to.setAttribute(key, value);
        } else {
            to.removeAttribute(key);
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        super.setEnabled(enabled);
        if (enabled) {
            getElement().getStyle().setOpacity(1);
        } else {
            getElement().getStyle().setOpacity(0.4);
        }
    }

    @Override
    public void setIcon(final String url) {
//        getElement().setAttribute("data", icon);

        ResourceCache.get(url, data -> {
            if (data != null) {
                getElement().setInnerHTML(data);
                final Element svg = getElement().getElementsByTagName("svg").getItem(0).cast();
//                svg.setAttribute("style", "fill:" + colourSet.getEnabled());
                svg.setAttribute("width", "18");
                svg.setAttribute("height", "18");
//                    svg.getStyle().setWidth(18, Unit.PX);
//                    svg.getStyle().setHeight(18, Unit.PX);
                svg.setTitle(getElement().getTitle());
            }
        });
    }

    @Override
    public void onBrowserEvent(final Event event) {
        // Should not act on button if disabled.
        if (isEnabled() == false) {
            // This can happen when events are bubbled up from non-disabled
            // children
            return;
        }

        final int type = DOM.eventGetType(event);
        switch (type) {
            case Event.ONCLICK:
                // If clicks are currently disallowed, keep it from bubbling or
                // being passed to the superclass.
                if (!allowClick) {
                    event.stopPropagation();
                    return;
                }
                break;
            case Event.ONMOUSEDOWN:
                if (event.getButton() == Event.BUTTON_LEFT) {
                    setFocus(true);
                    onClickStart();
                    DOM.setCapture(getElement());
                    isCapturing = true;
                    // Prevent dragging (on some browsers);
                    event.preventDefault();
                }
                break;
            case Event.ONMOUSEUP:
                if (isCapturing) {
                    isCapturing = false;
                    DOM.releaseCapture(getElement());
                    if (event.getButton() == Event.BUTTON_LEFT) {
                        onClick();
                    }
                }
                break;
            case Event.ONMOUSEMOVE:
                if (isCapturing) {
                    // Prevent dragging (on other browsers);
                    event.preventDefault();
                }
                break;
            case Event.ONMOUSEOUT:
                final Element to = DOM.eventGetToElement(event);
                if (getElement().isOrHasChild(DOM.eventGetTarget(event))
                        && (to == null || !getElement().isOrHasChild(to))) {
                    if (isCapturing) {
                        onClickCancel();
                    }
                    setHovering(false);
                }
                break;
            case Event.ONMOUSEOVER:
                if (getElement().isOrHasChild(DOM.eventGetTarget(event))) {
                    setHovering(true);
                    if (isCapturing) {
                        onClickStart();
                    }
                }
                break;
            case Event.ONBLUR:
                if (isFocusing) {
                    isFocusing = false;
                    onClickCancel();
                }
                break;
            case Event.ONLOSECAPTURE:
                if (isCapturing) {
                    isCapturing = false;
                    onClickCancel();
                }
                break;
        }

        super.onBrowserEvent(event);

        // Synthesize clicks based on keyboard events AFTER the normal key
        // handling.
        if ((event.getTypeInt() & Event.KEYEVENTS) != 0) {
            final char keyCode = (char) event.getKeyCode();
            switch (type) {
                case Event.ONKEYDOWN:
                    if (keyCode == ' ') {
                        isFocusing = true;
                        onClickStart();
                    }
                    break;
                case Event.ONKEYUP:
                    if (isFocusing && keyCode == ' ') {
                        isFocusing = false;
                        onClick();
                    }
                    break;
                case Event.ONKEYPRESS:
                    if (keyCode == '\n' || keyCode == '\r') {
                        onClickStart();
                        onClick();
                    }
                    break;
            }
        }
    }

    private void onClickStart() {
        getElement().addClassName("face--down");
    }

    private void onClickCancel() {
        getElement().removeClassName("face--down");
    }

    private void onClick() {
        // Allow the click we're about to synthesize to pass through to the
        // superclass and containing elements. Element.dispatchEvent() is
        // synchronous, so we simply set and clear the flag within this method.
        allowClick = true;

        // Mouse coordinates are not always available (e.g., when the click is
        // caused by a keyboard event).
        final NativeEvent evt = Document.get().createClickEvent(1, 0, 0, 0, 0, false, false, false, false);
        getElement().dispatchEvent(evt);

        allowClick = false;
    }

    private void setHovering(final boolean hovering) {
        if (isEnabled()) {
//            if (hovering) {
//                getElement().getStyle().setColor(colourSet.getHover());
//            } else {
//                getElement().getStyle().setColor(colourSet.getEnabled());
//            }
        }

        // if (hovering && hoverColor != null) {
        // face.getStyle().setBackgroundColor(hoverColor);
        // } else if (color != null) {
        // face.getStyle().setBackgroundColor(color);
        // }
    }

    @Override
    public void setVisible(final boolean visible) {
        super.setVisible(visible);
        if (!visible) {
//            if (isEnabled()) {
//            getElement().getStyle().setColor(colourSet.getEnabled());
//            }
        }
    }
}
