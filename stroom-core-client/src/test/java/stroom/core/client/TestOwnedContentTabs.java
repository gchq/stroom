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

import stroom.content.client.event.CloseContentTabEvent;
import stroom.widget.component.client.Layer;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;
import stroom.widget.tab.client.event.RequestCloseTabEvent;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestOwnedContentTabs {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<TabData> closed = new ArrayList<>();
    private ContentManager contentManager;

    @BeforeEach
    void setUp() {
        contentManager = new ContentManager(eventBus);
        eventBus.addHandler(CloseContentTabEvent.getType(), event -> closed.add(event.getTabData()));
    }

    @Test
    void theTabsStillOpenCloseWithWhatTheyBelongTo() {
        final OwnedContentTabs tabs = new OwnedContentTabs(contentManager);
        final TabData first = new TabDataImpl("First");
        final TabData second = new TabDataImpl("Second");
        tabs.open(first, Mockito.mock(Layer.class));
        tabs.open(second, Mockito.mock(Layer.class));

        // The user closes one
        RequestCloseTabEvent.fire(contentManager, first);
        assertThat(closed).containsExactly(first);

        // What they belong to goes (e.g. an embedded query's pane): before, its query's tab was
        // disposed while still open, so the other one is closed now, and the first not again
        tabs.closeAll();
        assertThat(closed).containsExactly(first, second);

        tabs.closeAll();
        assertThat(closed).containsExactly(first, second);
    }
}
