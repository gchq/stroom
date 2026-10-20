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
import stroom.gwt.workbench.framework.client.manager.addons.Addon;

import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

import java.util.ArrayList;
import java.util.List;

/// The addon panel below (or beside) the preview, with a tab for each addon (Controls, Actions,
/// Interactions and Accessibility), as in React Storybook. The selected tab is kept when the
/// story changes.
public class AddonPanel {

    private static final String TABS_ID = "wbm-panel-tabs";
    private static final String CONTENT_ID = "wbm-panel-content";
    private static final String ATTR_TAB = "data-tab";
    private static final String CLASS_ADDON = "wbm-addon";
    private static final String CLASS_ADDON_SELECTED = "wbm-addon--selected";

    private final Element tabsElement;
    private final List<Addon> addons;
    private final List<Element> addonElements = new ArrayList<>();
    private int selectedIndex;

    /// @param addons The addons, in tab order.
    public AddonPanel(final List<Addon> addons) {
        this.addons = new ArrayList<>(addons);
        final Document document = Document.get();
        tabsElement = document.getElementById(TABS_ID);
        final Element contentElement = document.getElementById(CONTENT_ID);
        for (final Addon addon : addons) {
            final DivElement element = document.createDivElement();
            element.setClassName(CLASS_ADDON);
            contentElement.appendChild(element);
            addonElements.add(element);
            addon.attach(element, this::renderTabs);
        }
        BrowserUtil.addListener(tabsElement, "click", event -> {
            final Element tab = BrowserUtil.closest(Element.as(event.getEventTarget()), "[" + ATTR_TAB + "]");
            if (tab != null) {
                select(Integer.parseInt(tab.getAttribute(ATTR_TAB)));
            }
        });
        select(0);
    }

    /// Shows an addon's tab.
    ///
    /// @param index The index of the addon.
    public void select(final int index) {
        selectedIndex = index;
        for (int i = 0; i < addonElements.size(); i++) {
            if (i == index) {
                addonElements.get(i).addClassName(CLASS_ADDON_SELECTED);
            } else {
                addonElements.get(i).removeClassName(CLASS_ADDON_SELECTED);
            }
        }
        renderTabs();
    }

    /// Moves the keyboard focus to the selected tab.
    public void focus() {
        final Element tab = getTab(selectedIndex);
        if (tab != null) {
            BrowserUtil.focus(tab);
        }
    }

    private Element getTab(final int index) {
        Element tab = tabsElement.getFirstChildElement();
        for (int i = 0; i < index && tab != null; i++) {
            tab = tab.getNextSiblingElement();
        }
        return tab;
    }

    /// @return The index of the tab with the keyboard focus, or -1 if no tab has it.
    private int getFocusedTabIndex() {
        final Element active = BrowserUtil.getActiveElement();
        if (active == null || !tabsElement.isOrHasChild(active)) {
            return -1;
        }
        final Element tab = BrowserUtil.closest(active, "[" + ATTR_TAB + "]");
        return tab != null
                ? Integer.parseInt(tab.getAttribute(ATTR_TAB))
                : -1;
    }

    private void renderTabs() {
        // Re-rendering replaces the tab buttons, e.g. when a badge changes, so give the focus back
        // to the same tab afterwards
        final int focusedIndex = getFocusedTabIndex();
        final SafeHtmlBuilder tabs = new SafeHtmlBuilder();
        for (int i = 0; i < addons.size(); i++) {
            final Addon addon = addons.get(i);
            final boolean selected = i == selectedIndex;
            tabs.appendHtmlConstant("<button type=\"button\" role=\"tab\" class=\"wbm-tab"
                                    + (selected
                    ? " wbm-tab--selected"
                    : "")
                                    + "\" aria-selected=\"" + selected + "\" " + ATTR_TAB + "=\"" + i + "\">")
                    .appendEscaped(addon.getTitle());
            final String badge = addon.getBadge();
            if (badge != null) {
                tabs.appendHtmlConstant("<span class=\"wbm-badge\">")
                        .appendEscaped(badge)
                        .appendHtmlConstant("</span>");
            }
            tabs.appendHtmlConstant("</button>");
        }
        tabsElement.setInnerSafeHtml(tabs.toSafeHtml());
        if (focusedIndex >= 0) {
            final Element tab = getTab(focusedIndex);
            if (tab != null) {
                tab.focus();
            }
        }
    }
}
