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

package stroom.query.client.result.vis;

import stroom.query.client.result.vis.VisTooltipPlacement.Direction;
import stroom.query.client.result.vis.VisTooltipPlacement.Placement;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Window;

/// Shows a visualisation's hover tooltip on the page rather than inside the visualisation's
/// frame, so that it is no longer cut off at the edges of the frame. The visualisation asks for
/// it through the frame's message bridge (`stroom.showTooltip` and `stroom.hideTooltip` in
/// `vis.js`), giving the tooltip's HTML and the frame-relative bounds of the element it
/// describes.
///
/// One tooltip serves the whole page, owned by the frame that last showed it. It is hidden when
/// its frame asks, when its frame goes away, on Escape, on a mouse press, and when the page
/// scrolls or is resized (WCAG 1.4.13). It is for sight only (`aria-hidden`), like
/// [stroom.widget.tooltip.client.FocusTooltip].
///
/// Visualisations come from trusted content packs, but their tooltips hold search result values
/// that were put into the HTML unescaped, so the HTML is reduced to a few text and layout
/// elements, with only their `class` and `style` attributes, before it is shown.
public final class VisTooltip {

    // Also a Stroom popup (popup.css), so that the tooltip looks as they do and, as they are, is
    // see-through when the user's preferences say so (the page's `transparency` class)
    private static final String CLASS_NAME = "popup vis-tooltip";
    private static final String CONTENT_CLASS_NAME = "vis-tooltip__content";
    private static final String SHOWN_CLASS_NAME = "vis-tooltip--shown";
    private static final int NO_OWNER = 0;

    private static Element tooltip;
    private static Element content;
    private static int ownerFrameId = NO_OWNER;

    private VisTooltip() {
        // Static utility
    }

    /// Shows the tooltip beside an element in a visualisation's frame.
    ///
    /// @param frameId     The id of the frame showing it, which then owns it.
    /// @param frame       The frame's element.
    /// @param html        The tooltip's content.
    /// @param rectInFrame The bounds of the element it describes, relative to the frame.
    /// @param direction   The side the visualisation would like it on, or null for no preference.
    public static void show(final int frameId,
                            final Element frame,
                            final String html,
                            final Rect rectInFrame,
                            final Direction direction) {
        final Element element = getOrCreateTooltip();
        nativeSetSanitisedContent(content, html);
        // Shown before it is placed, so that it can be measured
        element.addClassName(SHOWN_CLASS_NAME);

        final double frameLeft = boundingClientValue(frame, "left");
        final double frameTop = boundingClientValue(frame, "top");
        final Rect anchor = new Rect(
                frameTop + rectInFrame.getTop(),
                frameTop + rectInFrame.getBottom(),
                frameLeft + rectInFrame.getLeft(),
                frameLeft + rectInFrame.getRight());

        final Placement placement = VisTooltipPlacement.place(
                anchor,
                element.getOffsetWidth(),
                element.getOffsetHeight(),
                Window.getClientWidth(),
                Window.getClientHeight(),
                direction);
        CssProperties.set(element, "--vis-tooltip__left", placement.left() + "px");
        CssProperties.set(element, "--vis-tooltip__top", placement.top() + "px");
        ownerFrameId = frameId;
    }

    /// Hides the tooltip, if the given frame owns it.
    ///
    /// @param frameId The id of the frame asking.
    public static void hide(final int frameId) {
        if (ownerFrameId == frameId) {
            hideNow();
        }
    }

    // Called from the page listeners in nativeInstallHideListeners as well
    private static void hideNow() {
        if (tooltip != null) {
            tooltip.removeClassName(SHOWN_CLASS_NAME);
        }
        ownerFrameId = NO_OWNER;
    }

    private static Element getOrCreateTooltip() {
        if (tooltip == null) {
            tooltip = Document.get().createDivElement();
            tooltip.setClassName(CLASS_NAME);
            // Hidden until shown (vis-tooltip--shown); assistive technology reads the points' own names
            tooltip.setAttribute("aria-hidden", "true");
            content = Document.get().createDivElement();
            content.setClassName(CONTENT_CLASS_NAME);
            tooltip.appendChild(content);
            Document.get().getBody().appendChild(tooltip);
            nativeInstallHideListeners();
        }
        return tooltip;
    }

    private static native double boundingClientValue(Element element, String side) /*-{
        return element.getBoundingClientRect()[side];
    }-*/;

    private static native void nativeInstallHideListeners() /*-{
        var hide = $entry(function () {
            @stroom.query.client.result.vis.VisTooltip::hideNow()();
        });
        $doc.addEventListener('mousedown', hide, true);
        $doc.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                hide();
            }
        }, true);
        $wnd.addEventListener('scroll', hide, true);
        $wnd.addEventListener('resize', hide);
    }-*/;

    // Parses the HTML in a template, whose content is inert (no scripts run and no images load),
    // keeps only the allowed elements and attributes, and puts what is left in the target (the
    // tooltip's content). Elements that are not allowed are replaced by their content, except those
    // whose content is not text, which are dropped
    private static native void nativeSetSanitisedContent(Element target, String html) /*-{
        var allowedTags = {
            DIV: true, SPAN: true, P: true, BR: true, B: true, STRONG: true, I: true, EM: true,
            SMALL: true, SUB: true, SUP: true, UL: true, OL: true, LI: true,
            TABLE: true, THEAD: true, TBODY: true, TR: true, TH: true, TD: true
        };
        var droppedTags = {
            SCRIPT: true, STYLE: true, TEMPLATE: true, IFRAME: true, OBJECT: true, EMBED: true,
            SVG: true, MATH: true, NOSCRIPT: true
        };
        var allowedAttributes = { 'class': true, 'style': true };

        var clean = function (parent) {
            var children = Array.prototype.slice.call(parent.childNodes);
            for (var i = 0; i < children.length; i++) {
                var node = children[i];
                if (node.nodeType === 1) {
                    var tag = node.tagName.toUpperCase();
                    if (droppedTags[tag]) {
                        parent.removeChild(node);
                    } else if (!allowedTags[tag]) {
                        clean(node);
                        while (node.firstChild) {
                            parent.insertBefore(node.firstChild, node);
                        }
                        parent.removeChild(node);
                    } else {
                        var attributes = Array.prototype.slice.call(node.attributes);
                        for (var j = 0; j < attributes.length; j++) {
                            if (!allowedAttributes[attributes[j].name.toLowerCase()]) {
                                node.removeAttribute(attributes[j].name);
                            }
                        }
                        clean(node);
                    }
                } else if (node.nodeType !== 3) {
                    // Comments and anything else that isn't text
                    parent.removeChild(node);
                }
            }
        };

        var template = $doc.createElement('template');
        template.innerHTML = html || '';
        clean(template.content);
        while (target.firstChild) {
            target.removeChild(target.firstChild);
        }
        target.appendChild(template.content);
    }-*/;
}
