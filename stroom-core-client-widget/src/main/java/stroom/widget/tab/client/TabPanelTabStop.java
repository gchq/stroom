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

package stroom.widget.tab.client;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;

/// Makes a tab panel a tab stop while it holds no tab stop of its own, as the ARIA tabs pattern
/// asks, so that Tab from the tab list reaches the selected tab's content rather than passing over
/// it (and a screen reader user lands in it). A panel that holds a tab stop isn't one, as Tab
/// reaches its content anyway.
///
/// A tab's content is often shown after its tab is selected (e.g. once it has loaded), so the
/// panel is watched for changes to what it holds and checked again whenever it changes. It is
/// checked again too when it comes to show (its size changes), as a panel set and filled before it
/// is in the page, or in a hidden tab, holds nothing that shows; and while a box round it is
/// hidden (`visibility: hidden`, as a dialog is while it is placed), its fields count as showing.
/// It was checked only as what it held changed, so a dialog's panel of fields, filled before the
/// dialog showed, stayed a tab stop: Tab went from its tab to the panel, then the first field.
final class TabPanelTabStop {

    private TabPanelTabStop() {
        // Static utility
    }

    /// Starts watching a panel, checking it now and whenever what it holds changes.
    ///
    /// @param panel The tab panel.
    /// @return The watchers, to give [#unwatch] when the panel is no longer the tab bar's.
    static JavaScriptObject watch(final Element panel) {
        update(panel);
        return observe(panel);
    }

    /// Stops watching a panel, and takes it out of the tab order.
    ///
    /// @param panel   The tab panel.
    /// @param watcher What [#watch] gave, or null.
    static void unwatch(final Element panel, final JavaScriptObject watcher) {
        if (watcher != null) {
            disconnect(watcher);
        }
        panel.removeAttribute("tabindex");
    }

    // Changed only when it differs, as a change to the panel's own tabindex is a change the watcher
    // sees, which would otherwise check it again without end
    private static void update(final Element panel) {
        if (!hasBox(panel)) {
            // Not shown (not yet in the page, or in a hidden tab): what shows in it can't be told,
            // so it is left as it is, and checked when it shows
            return;
        }
        final boolean tabStop = !holdsTabStop(panel);
        if (tabStop != panel.hasAttribute("tabindex")) {
            if (tabStop) {
                panel.setTabIndex(0);
            } else {
                panel.removeAttribute("tabindex");
            }
        }
    }

    private static native boolean hasBox(Element panel) /*-{
        return panel.getClientRects().length > 0;
    }-*/;

    // Whether it holds a tab stop that shows. While a box round it is hidden (visibility: hidden,
    // e.g. a dialog being placed), all it holds is hidden, so its fields count whatever their
    // visibility: the box shows them all at once, a change the panel's watchers don't see
    private static native boolean holdsTabStop(Element panel) /*-{
        var view = panel.ownerDocument.defaultView;
        if (view.getComputedStyle(panel).visibility !== 'hidden') {
            return @stroom.widget.util.client.TabOrder::tabStops(*)(panel).length > 0;
        }
        var selector = 'a[href], button, input, select, textarea, [tabindex]';
        return Array.prototype.some.call(panel.querySelectorAll(selector), function (element) {
            return element.tabIndex >= 0 && !element.disabled && element.getClientRects().length > 0;
        });
    }-*/;

    private static native JavaScriptObject observe(Element panel) /*-{
        var update = function () {
            @stroom.widget.tab.client.TabPanelTabStop::update(*)(panel);
        };
        // As content is added, removed, shown, hidden, enabled or disabled
        var mutations = new MutationObserver(update);
        mutations.observe(panel, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['style', 'tabindex', 'hidden', 'disabled']
        });
        // As the panel comes to show (put in the page, or its tab shown), or is hidden
        var sizes = new ResizeObserver(update);
        sizes.observe(panel);
        return {mutations: mutations, sizes: sizes};
    }-*/;

    private static native void disconnect(JavaScriptObject watcher) /*-{
        watcher.mutations.disconnect();
        watcher.sizes.disconnect();
    }-*/;
}
