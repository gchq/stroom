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

package stroom.search.elastic.client.presenter;

import stroom.widget.tab.client.presenter.TabData;

import com.gwtplatform.mvp.client.UiHandlers;

import java.util.List;

/// Handles user input on the Elasticsearch index Fields tab that is not part of the field list itself.
public interface ElasticIndexFieldListUiHandlers extends UiHandlers {

    /// @param text the text the field name must contain
    void onNameFilterChange(String text);

    /// @param types the type filter values ticked, or empty for all types
    void onTypeFilterChange(List<String> types);

    /// @param tab the details tab the user selected
    void onDetailsTabSelected(TabData tab);
}
