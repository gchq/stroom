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

package stroom.core.client;

import stroom.widget.component.client.Layer;
import stroom.widget.tab.client.TabData;

import java.util.ArrayList;
import java.util.List;

/// Content tabs that belong to something else, e.g. the query a dashboard's embedded query pane
/// opens, whose Save puts it back into the pane. Each closes as any tab does, and those still open
/// are closed with [#closeAll()] when what they belong to goes, so that none is left open over
/// content that has been disposed.
public final class OwnedContentTabs {

    private final ContentManager contentManager;
    private final List<TabData> open = new ArrayList<>();

    /// @param contentManager Opens and closes the tabs.
    public OwnedContentTabs(final ContentManager contentManager) {
        this.contentManager = contentManager;
    }

    /// Opens a tab, owned (as any tab's content is) until it is closed.
    ///
    /// @param tab   The tab.
    /// @param layer What it shows.
    public void open(final TabData tab, final Layer layer) {
        open.add(tab);
        contentManager.open(event -> {
            event.getCallback().closeTab(true);
            open.remove(tab);
        }, tab, layer);
    }

    /// Closes the tabs still open, without asking about unsaved changes.
    public void closeAll() {
        final List<TabData> tabs = new ArrayList<>(open);
        open.clear();
        for (final TabData tab : tabs) {
            contentManager.forceClose(tab, true);
        }
    }
}
