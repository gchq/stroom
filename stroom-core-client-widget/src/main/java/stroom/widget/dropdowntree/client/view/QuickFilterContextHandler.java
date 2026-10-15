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

package stroom.widget.dropdowntree.client.view;

import java.util.function.Consumer;

/**
 * What a {@link QuickFilter} needs from the screen it sits on to offer a drop-down of recently
 * used filters.
 * <p>
 * The widget lives in a module with no access to the REST layer or the event bus, so it cannot
 * fetch history or show a menu itself. It knows <em>when</em> - the user clicked the arrow, the
 * user committed a filter - and delegates the <em>how</em> to the presenter through this. A
 * {@code QuickFilter} with no handler shows no arrow and records nothing.
 */
public interface QuickFilterContextHandler {

    /**
     * The user clicked the drop-down arrow (or pressed Alt+Down). Show the recently used filters
     * anchored below {@code quickFilter}; call {@code onSelect} with the one they choose.
     */
    void showRecentFilters(QuickFilter quickFilter, Consumer<String> onSelect);

    /**
     * The user committed {@code filterText} and the server accepted it. Called once per distinct
     * commit, never for the debounced as-you-type queries, and never for text the server
     * rejected.
     */
    void recordUse(String filterText);
}
