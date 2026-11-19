/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.impl.sql.client.component;

import stroom.docref.DocRef;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows a statistic store's settings in its Settings tab, read only as the store is, and tells the
/// store of each change.
public class StatisticsDataSourceSettingsController extends Controller {

    private final StatisticsDataSourceSettings view;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The tab.
    @Inject
    public StatisticsDataSourceSettingsController(final EventBus eventBus, final StatisticsDataSourceSettings view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnChange(this::markChanged);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Shows a store's settings.
    ///
    /// @param docRef The store.
    /// @param doc    Its settings.
    public void read(final DocRef docRef, final StatisticStoreDoc doc) {
        view.read(doc);
    }

    /// Writes the settings shown into a copy of a store.
    ///
    /// @param doc The store.
    /// @return A copy of it with the settings shown.
    public StatisticStoreDoc write(final StatisticStoreDoc doc) {
        return view.write(doc);
    }
}
