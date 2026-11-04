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

import stroom.widget.util.client.TabOrder;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;

/// Makes a tab panel a tab stop while it holds no tab stop of its own, as the ARIA tabs pattern
/// asks, so that Tab from the tab list reaches the selected tab's content rather than passing over
/// it (and a screen reader user lands in it). A panel that holds a tab stop isn't one, as Tab
/// reaches its content anyway.
///
/// A tab's content is often shown after its tab is selected (e.g. once it has loaded), so the
/// panel is watched for changes to what it holds and checked again whenever it changes.
final class TabPanelTabStop {

    private TabPanelTabStop() {
        // Static utility
    }

    /// Starts watching a panel, checking it now and whenever what it holds changes.
    ///
    /// @param panel The tab panel.
    /// @return The watcher, to give [#unwatch] when the panel is no longer the tab bar's.
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
        final boolean tabStop = TabOrder.tabStops(panel).length() == 0;
        if (tabStop != panel.hasAttribute("tabindex")) {
            if (tabStop) {
                panel.setTabIndex(0);
            } else {
                panel.removeAttribute("tabindex");
            }
        }
    }

    private static native JavaScriptObject observe(Element panel) /*-{
        // As content is added, removed, shown, hidden, enabled or disabled
        var observer = new MutationObserver(function () {
            @stroom.widget.tab.client.TabPanelTabStop::update(*)(panel);
        });
        observer.observe(panel, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ['style', 'tabindex', 'hidden', 'disabled']
        });
        return observer;
    }-*/;

    private static native void disconnect(JavaScriptObject watcher) /*-{
        watcher.disconnect();
    }-*/;
}
