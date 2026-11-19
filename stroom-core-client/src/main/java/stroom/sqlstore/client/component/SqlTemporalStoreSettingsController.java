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

package stroom.sqlstore.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.sqlstore.shared.SqlTemporalStoreResource;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A SQL temporal store's Settings tab ([SqlTemporalStoreSettings]): counts the store's entries,
/// and removes them all when asked, once the user confirms.
public class SqlTemporalStoreSettingsController extends Controller {

    private static final SqlTemporalStoreResource SQL_STORE_RESOURCE = GWT.create(SqlTemporalStoreResource.class);

    private final SqlTemporalStoreSettings view;
    private final RestFactory restFactory;
    private DocRef docRef;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The tab.
    /// @param restFactory Counts and clears the store's entries.
    @Inject
    public SqlTemporalStoreSettingsController(final EventBus eventBus,
                                              final SqlTemporalStoreSettings view,
                                              final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        view.setOnReset(this::reset);
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    /// Shows how many entries a store holds.
    ///
    /// @param docRef The store.
    /// @param doc    Its settings.
    public void read(final DocRef docRef, final SqlTemporalStoreDoc doc) {
        this.docRef = docRef;
        updateCount();
    }

    private void reset() {
        if (docRef != null) {
            ConfirmEvent.fire(this,
                    "This will remove all entries from the store. Are you sure you want to do this?",
                    ok -> {
                        if (ok) {
                            restFactory
                                    .create(SQL_STORE_RESOURCE)
                                    .method(res -> res.clear(docRef))
                                    .onSuccess(r -> updateCount())
                                    .owner(this)
                                    .exec();
                        }
                    });
        }
    }

    private void updateCount() {
        if (docRef != null) {
            restFactory
                    .create(SQL_STORE_RESOURCE)
                    .method(res -> res.count(docRef))
                    .onSuccess(view::setCount)
                    .owner(this)
                    .exec();
        }
    }
}
