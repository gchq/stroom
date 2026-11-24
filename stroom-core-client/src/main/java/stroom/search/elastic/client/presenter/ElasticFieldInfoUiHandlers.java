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

import com.gwtplatform.mvp.client.UiHandlers;

/// Handles user input on the Info tab of the Elasticsearch index Fields tab.
public interface ElasticFieldInfoUiHandlers extends UiHandlers {

    /// Called when the user changes the statistics time range.
    void onTimeRangeChange();

    /// @param sampleSize the approximate number of documents to sample at random that the user chose
    void onSampleSizeChange(int sampleSize);

    /// @param exact `true` if the user chose to calculate the statistics across every document, `false` for a random
    ///              sample
    void onExactChange(boolean exact);

    /// @param topValuesCount how many of the most common values the user chose to show
    void onTopValuesCountChange(int topValuesCount);

    /// @param filter the text the user typed to filter the most common values by
    void onTopValuesFilterChange(String filter);

    /// Called when the user asks for the statistics to be fetched again, by refreshing or retrying.
    void onRefreshStats();
}
