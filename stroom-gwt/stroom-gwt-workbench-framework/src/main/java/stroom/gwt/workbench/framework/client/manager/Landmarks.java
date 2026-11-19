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

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.List;

/// Moves the keyboard focus between the manager's landmark regions (sidebar, toolbar, canvas and
/// addon panel), for Storybook's 'Go to next/previous landmark' shortcuts (F6 / shift+F6).
public final class Landmarks {

    // The regions in order, each with the element to focus in it
    private static final String[][] REGIONS = {
            {"workbench-sidebar-region", "workbench-explorer-menu"},
            {"wbm-toolbar", "wbm-reload"},
            {"workbench-preview-wrapper", "workbench-preview-iframe"},
            {"workbench-panel-root", "wbm-panel-tabs"},
    };

    private Landmarks() {
        // Static utility
    }

    /// Focuses the next or previous visible landmark after the one with the focus.
    ///
    /// @param forwards True for the next landmark, false for the previous one.
    public static void focusNext(final boolean forwards) {
        final List<String[]> visible = new ArrayList<>();
        for (final String[] region : REGIONS) {
            final Element element = Document.get().getElementById(region[0]);
            if (element != null && element.getOffsetWidth() > 0 && element.getOffsetHeight() > 0) {
                visible.add(region);
            }
        }
        if (visible.isEmpty()) {
            return;
        }

        int current = -1;
        final Element active = getActiveElement();
        for (int i = 0; i < visible.size(); i++) {
            final Element region = Document.get().getElementById(visible.get(i)[0]);
            if (active != null && region.isOrHasChild(active)) {
                current = i;
            }
        }
        final int size = visible.size();
        final int next = forwards
                ? (current + 1) % size
                : (current - 1 + size) % size;
        focus(Document.get().getElementById(visible.get(next)[1]));
    }

    private static void focus(final Element element) {
        if (element == null) {
            return;
        }
        if ("IFRAME".equalsIgnoreCase(element.getTagName())) {
            BrowserUtil.focusIframe(element.cast());
            return;
        }
        if (element.getTabIndex() < 0 && !element.hasAttribute("href")) {
            // Focus the first focusable child, e.g. the first tab
            final Element child = element.getFirstChildElement();
            if (child != null) {
                BrowserUtil.focus(child);
                return;
            }
        }
        BrowserUtil.focus(element);
    }

    private static native Element getActiveElement() /*-{
        return $doc.activeElement;
    }-*/;
}
