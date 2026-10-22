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

package stroom.sqlstore.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.sqlstore.shared.SqlTemporalStoreResource;
import stroom.widget.component.client.Contexts;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class SqlTemporalStoreSettingsPresenter
        extends DocPresenter<SqlTemporalStoreSettingsPresenter.SqlTemporalStoreSettingsView, SqlTemporalStoreDoc>
        implements SqlTemporalStoreSettingsUiHandlers {

    private static final SqlTemporalStoreResource SQL_STORE_RESOURCE = GWT.create(SqlTemporalStoreResource.class);

    private final RestFactory restFactory;

    @Inject
    public SqlTemporalStoreSettingsPresenter(
            final EventBus eventBus,
            final SqlTemporalStoreSettingsView view,
            final RestFactory restFactory) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);
        this.restFactory = restFactory;
        view.setUiHandlers(this);
    }

    @Override
    protected void onRead(final DocRef docRef, final SqlTemporalStoreDoc doc) {
        updateCount(docRef);
    }

    @Override
    protected SqlTemporalStoreDoc onWrite(final SqlTemporalStoreDoc doc) {
        return doc;
    }

    @Override
    public void onReset() {
        if (getEntity() != null) {
            ConfirmEvent.fire(this,
                    "This will remove all entries from the store. Are you sure you want to do this?",
                    ok -> {
                        if (ok) {
                            restFactory
                                    .create(SQL_STORE_RESOURCE)
                                    .method(res -> res.clear(getEntity().asDocRef()))
                                    .onSuccess(r -> updateCount(getEntity().asDocRef()))
                                    .exec();
                        }
                    });
        }
    }

    private void updateCount(final DocRef docRef) {
        if (docRef != null) {
            restFactory
                    .create(SQL_STORE_RESOURCE)
                    .method(res -> res.count(docRef))
                    .onSuccess(count -> getView().setCount(count))
                    .exec();
        }
    }

    public interface SqlTemporalStoreSettingsView
            extends View, HasUiHandlers<SqlTemporalStoreSettingsUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        void setCount(long count);
    }
}
