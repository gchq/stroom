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

package stroom.gwt.workbench.framework.client.manager;

import stroom.gwt.workbench.framework.client.BrowserUtil;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;

import java.util.function.Consumer;

/// A floating panel (e.g. a menu) anchored to a button, styled like Storybook's popovers. Only
/// one popover is open at a time. It closes when the user clicks outside it or presses Escape.
public final class Popover {

    private static final String CLASS_NAME = "wbm-popover";
    private static final int GAP_PX = 8;
    private static final int MARGIN_PX = 8;

    private static Popover current;

    private final Element anchor;
    private final Element element;
    private final Align align;
    private final HandlerRegistration previewRegistration;
    private Runnable closeHandler;

    private Popover(final Element anchor,
                    final SafeHtml content,
                    final Align align,
                    final Consumer<Element> clickHandler) {
        this.anchor = anchor;
        this.align = align;

        final DivElement div = Document.get().createDivElement();
        div.setClassName(CLASS_NAME);
        div.setInnerSafeHtml(content);
        Document.get().getBody().appendChild(div);
        this.element = div;
        BrowserUtil.addListener(element, "click", event -> {
            final EventTarget target = event.getEventTarget();
            if (target != null && Element.is(target)) {
                clickHandler.accept(Element.as(target));
            }
        });
        position();
        anchor.setAttribute("aria-expanded", "true");

        previewRegistration = Event.addNativePreviewHandler(preview -> {
            final NativeEvent event = preview.getNativeEvent();
            final int type = preview.getTypeInt();
            if (type == Event.ONMOUSEDOWN && !isInside(event)) {
                hide();
            } else if (type == Event.ONKEYDOWN && event.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                event.preventDefault();
                hide();
            }
        });
    }

    /// Shows a popover, closing any other that is open. If a popover is already open for the
    /// same anchor then it is closed instead, so the anchor toggles it.
    ///
    /// @param anchor       The element to show the popover next to.
    /// @param content      The content of the popover.
    /// @param align        How to align the popover with the anchor.
    /// @param clickHandler Called with the clicked element for clicks inside the popover.
    /// @return The new popover, or null if the popover was toggled closed.
    public static Popover toggle(final Element anchor,
                                 final SafeHtml content,
                                 final Align align,
                                 final Consumer<Element> clickHandler) {
        if (current != null && current.anchor == anchor) {
            current.hide();
            return null;
        }
        hideCurrent();
        current = new Popover(anchor, content, align, clickHandler);
        return current;
    }

    /// Closes the open popover, if there is one.
    public static void hideCurrent() {
        if (current != null) {
            current.hide();
        }
    }

    /// @return True if a popover is open.
    public static boolean isAnyOpen() {
        return current != null;
    }

    /// Replaces the content of the popover, e.g. to show a changed state.
    ///
    /// @param content The new content.
    public void setContent(final SafeHtml content) {
        element.setInnerSafeHtml(content);
        position();
    }

    /// @return The popover's element.
    public Element getElement() {
        return element;
    }

    /// @param closeHandler Called when the popover closes.
    public void setCloseHandler(final Runnable closeHandler) {
        this.closeHandler = closeHandler;
    }

    /// Closes the popover.
    public void hide() {
        previewRegistration.removeHandler();
        element.removeFromParent();
        anchor.setAttribute("aria-expanded", "false");
        if (current == this) {
            current = null;
        }
        if (closeHandler != null) {
            closeHandler.run();
        }
    }

    private boolean isInside(final NativeEvent event) {
        final EventTarget target = event.getEventTarget();
        if (target == null || !Element.is(target)) {
            return false;
        }
        final Element targetElement = Element.as(target);
        return element.isOrHasChild(targetElement) || anchor.isOrHasChild(targetElement);
    }

    private void position() {
        final int width = element.getOffsetWidth();
        final int height = element.getOffsetHeight();
        final int windowWidth = Window.getClientWidth();
        final int windowHeight = Window.getClientHeight();

        int left;
        switch (align) {
            case END:
                left = anchor.getAbsoluteRight() - width;
                break;
            case CENTER:
                left = anchor.getAbsoluteLeft() + ((anchor.getOffsetWidth() - width) / 2);
                break;
            default:
                left = anchor.getAbsoluteLeft();
                break;
        }
        left = Math.max(MARGIN_PX, Math.min(left, windowWidth - width - MARGIN_PX));

        int top = anchor.getAbsoluteBottom() + GAP_PX;
        if (top + height > windowHeight - MARGIN_PX) {
            // Not enough room below so show it above
            top = Math.max(MARGIN_PX, anchor.getAbsoluteTop() - GAP_PX - height);
        }

        element.getStyle().setProperty("left", left + "px");
        element.getStyle().setProperty("top", top + "px");
    }


    // --------------------------------------------------------------------------------


    /// How a popover is aligned horizontally with its anchor.
    public enum Align {
        /// The left edges line up.
        START,
        /// The centres line up.
        CENTER,
        /// The right edges line up.
        END
    }
}
