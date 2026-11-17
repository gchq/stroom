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

package stroom.widget.datagrid.client;

/// A list that can be told whether a filter or search is set on its rows, so that with no rows it
/// says "No matches" rather than that there is nothing. [DataGrid] is one; a component that shows
/// one passes it on, so that a filter outside it (`QuickFilter.addFilteredGrid`) can reach it.
public interface HasFiltered {

    /// @param filtered Whether a filter or search is set.
    void setFiltered(boolean filtered);
}
