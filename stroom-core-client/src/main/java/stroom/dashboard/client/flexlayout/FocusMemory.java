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

package stroom.dashboard.client.flexlayout;

import stroom.dashboard.client.flexlayout.Splitter.SplitInfo;
import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.client.model.MutableLayoutConfig;
import stroom.dashboard.client.model.MutableTabConfig;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.Widget;

import java.util.Map;

/// Remembers which panel of a [FlexLayout] the keyboard focus was last in (by its selected tab),
/// and whether it was on the splitter after that panel, so the focus can be put back there after
/// the layout is rebuilt (e.g. when a panel is maximised or restored from its tab's menu), which
/// removes the element that had it.
final class FocusMemory {

    // How often, and how many times, to try to put the focus back after the layout is rebuilt
    private static final int RESTORE_FOCUS_DELAY_MS = 25;
    private static final int RESTORE_FOCUS_TRIES = 20;

    private final Map<MutableLayoutConfig, TabLayout> layoutToWidgetMap;
    private final Map<SplitInfo, Splitter> splitToWidgetMap;
    private final Widget designSurface;

    private MutableTabConfig focusedTab;
    private boolean focusedSplitter;

    /// @param layoutToWidgetMap The layout's panels.
    /// @param splitToWidgetMap  The layout's splitters.
    /// @param designSurface     What the panels and splitters shown are in.
    FocusMemory(final Map<MutableLayoutConfig, TabLayout> layoutToWidgetMap,
                final Map<SplitInfo, Splitter> splitToWidgetMap,
                final Widget designSurface) {
        this.layoutToWidgetMap = layoutToWidgetMap;
        this.splitToWidgetMap = splitToWidgetMap;
        this.designSurface = designSurface;
    }

    /// Notes the panel that an element taking the focus is in, or the panel before it if it is a
    /// splitter.
    ///
    /// @param target   The element taking the focus.
    /// @param splitter The splitter that is (or has) the element, or null if it isn't in one.
    void remember(final Element target, final Splitter splitter) {
        if (splitter != null) {
            final TabLayout before = splitter.getPanelBefore();
            if (before != null) {
                focusedTab = getSelectedTabConfig(before);
                focusedSplitter = focusedTab != null;
            }
        } else {
            for (final TabLayout tabLayout : layoutToWidgetMap.values()) {
                if (tabLayout.getElement().isOrHasChild(target)) {
                    focusedTab = getSelectedTabConfig(tabLayout);
                    focusedSplitter = false;
                    return;
                }
            }
        }
    }

    /// After the layout is rebuilt, puts the focus back on the panel (or the splitter after it) it
    /// was in, if it was lost with the elements that were removed (so not if something else, e.g.
    /// the toolbar button that changed the design mode, has it). A menu that rebuilt the layout
    /// (e.g. a tab's Maximise) may still be closing, and then gives the focus back to the removed
    /// element, which loses it, so while the focus is in a popup this waits (for up to
    /// [#RESTORE_FOCUS_TRIES] tries).
    void restoreLater() {
        final int[] tries = {0};
        Scheduler.get().scheduleFixedDelay(() -> {
            tries[0]++;
            if (isFocusLost()) {
                restore();
                return false;
            }
            return isFocusInPopup() && tries[0] < RESTORE_FOCUS_TRIES;
        }, RESTORE_FOCUS_DELAY_MS);
    }

    private void restore() {
        if (focusedTab == null) {
            return;
        }
        for (final TabLayout tabLayout : layoutToWidgetMap.values()) {
            if (tabLayout.getTabLayoutConfig().getTabs().contains(focusedTab)) {
                if (focusedSplitter) {
                    for (final Splitter splitter : splitToWidgetMap.values()) {
                        if (splitter.getPanelBefore() == tabLayout && splitter.getParent() == designSurface) {
                            splitter.getElement().focus();
                            return;
                        }
                    }
                }
                tabLayout.getTabBar().focus();
                return;
            }
        }
    }

    private static MutableTabConfig getSelectedTabConfig(final TabLayout tabLayout) {
        if (tabLayout.getTabBar().getSelectedTab() instanceof final DashboardPane component) {
            return component.getTabConfig();
        }
        return null;
    }

    // Whether the focus is in a popup, e.g. a menu that is closing
    private static native boolean isFocusInPopup() /*-{
        var active = $doc.activeElement;
        return !!active && !!active.closest('.popupContent, [role="menu"], [role="menuitem"]');
    }-*/;

    // Whether the focus is on nothing, as when the element that had it is removed
    private static native boolean isFocusLost() /*-{
        var active = $doc.activeElement;
        return !active || active === $doc.body || !active.isConnected;
    }-*/;
}
