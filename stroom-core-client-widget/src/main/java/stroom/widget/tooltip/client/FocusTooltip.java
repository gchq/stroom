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

package stroom.widget.tooltip.client;

/// Shows an icon button's name beside it when it has the keyboard focus, as browsers only show a
/// `title` tooltip on mouse hover. Without it, a keyboard user moving along a toolbar of icons
/// sees no names (WCAG 2.4.7 and 1.4.13).
///
/// One tooltip serves the whole page. It is shown when an icon button (`.toolbar-button`, or a
/// floating action button's face) gets focus from the keyboard (`:focus-visible`), with the
/// button's `title` (else its `aria-label`), unless the last input was a pointer, and hidden when
/// the focus leaves, on Escape, when the page scrolls or is resized, and on a mouse press. Mouse
/// users keep the browser's own `title` tooltip. The tooltip is for sight only (`aria-hidden`):
/// screen readers already hear the name.
///
/// Its listeners are on the document for the life of the page, installed once by the button
/// classes ([#install()]), and refer to no widget, so there is nothing to release.
public final class FocusTooltip {

    private static final String BUTTON_SELECTOR = ".toolbar-button, .fab-button > .fab-button__face";

    private static boolean installed;

    private FocusTooltip() {
        // Static utility
    }

    /// Installs the tooltip on the page, if it isn't already.
    public static void install() {
        if (!installed) {
            installed = true;
            nativeInstall(BUTTON_SELECTOR);
        }
    }

    private static native void nativeInstall(String buttonSelector) /*-{
        var doc = $doc;
        var tooltip = null;
        var gap = 4;
        // Whether the last input was a pointer (mouse, pen or touch) rather than a key. A button
        // focused by a click then counts as focused by the mouse, even when the browser says it is
        // :focus-visible (as it does when script moves the focus on from a button the keyboard
        // focused, e.g. an icon button focusing itself on mouse down)
        var pointerInput = false;

        var hide = function () {
            if (tooltip) {
                tooltip.classList.remove('focus-tooltip--shown');
            }
        };

        var show = function (button, text) {
            if (!tooltip) {
                tooltip = doc.createElement('div');
                tooltip.className = 'focus-tooltip';
                tooltip.setAttribute('aria-hidden', 'true');
                doc.body.appendChild(tooltip);
            }
            tooltip.textContent = text;
            tooltip.classList.add('focus-tooltip--shown');
            // Below the button, centred, kept on screen; above it if there is no room below
            var rect = button.getBoundingClientRect();
            var width = tooltip.offsetWidth;
            var height = tooltip.offsetHeight;
            var view = doc.documentElement;
            var left = rect.left + (rect.width - width) / 2;
            left = Math.max(gap, Math.min(left, view.clientWidth - width - gap));
            var top = rect.bottom + gap;
            if (top + height > view.clientHeight - gap) {
                top = rect.top - height - gap;
            }
            // Its place, as custom properties its class reads (focus-tooltip.css)
            tooltip.style.setProperty('--focus-tooltip__left', left + 'px');
            tooltip.style.setProperty('--focus-tooltip__top', top + 'px');
        };

        doc.addEventListener('focusin', function (event) {
            var button = event.target;
            if (!pointerInput && button && button.matches && button.matches(buttonSelector)
                && button.matches(':focus-visible')) {
                var text = button.getAttribute('title') || button.getAttribute('aria-label');
                if (text) {
                    show(button, text);
                    return;
                }
            }
            hide();
        }, true);
        doc.addEventListener('focusout', hide, true);
        doc.addEventListener('pointerdown', function () {
            pointerInput = true;
            hide();
        }, true);
        doc.addEventListener('mousedown', hide, true);
        doc.addEventListener('keydown', function (event) {
            pointerInput = false;
            if (event.key === 'Escape') {
                hide();
            }
        }, true);
        $wnd.addEventListener('scroll', hide, true);
        $wnd.addEventListener('resize', hide);
    }-*/;
}
