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

package stroom.floormap.client.component;

import stroom.docref.DocRef;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A floor map event store's Settings tab ([FloorMapEventStoreSettings]), read only in a store the
/// user can't change.
public class FloorMapEventStoreSettingsController extends Controller {

    private final FloorMapEventStoreSettings view;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The tab.
    @Inject
    public FloorMapEventStoreSettingsController(final EventBus eventBus, final FloorMapEventStoreSettings view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnChange(this::markChanged);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Shows a floor map event store's settings.
    ///
    /// @param docRef The document.
    /// @param doc Its settings.
    public void read(final DocRef docRef, final FloorMapEventStoreDoc doc) {
        view.read(doc);
    }

    /// Writes the settings shown into a copy of a floor map event store.
    ///
    /// @param doc The document.
    /// @return A copy of it with the settings shown.
    public FloorMapEventStoreDoc write(final FloorMapEventStoreDoc doc) {
        return view.write(doc);
    }
}
