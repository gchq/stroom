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

package stroom.dashboard.client.main;

import stroom.docref.DocRef;
import stroom.widget.component.client.Listeners;

import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.function.Consumer;
import javax.inject.Inject;

public class IndexLoader {

    // Told when a data source is loaded: the query, and the tables that show its results
    private final Listeners<IndexLoader> listeners = new Listeners<>();

    private DocRef loadedDataSourceRef;

    @Inject
    public IndexLoader() {
        // Made by GIN
    }

    /// Listens for a data source being loaded, until the registration is removed.
    ///
    /// @param listener Told this loader, once it has loaded one.
    /// @return The registration, for the listener's owner to own.
    public HandlerRegistration addChangeListener(final Consumer<IndexLoader> listener) {
        return listeners.add(listener);
    }

    public void loadDataSource(final DocRef dataSourceRef) {
        loadedDataSourceRef = dataSourceRef;
        listeners.fire(this);
    }

    public DocRef getLoadedDataSourceRef() {
        return loadedDataSourceRef;
    }
}
