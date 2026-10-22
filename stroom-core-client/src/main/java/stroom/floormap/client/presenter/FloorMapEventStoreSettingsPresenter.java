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

package stroom.floormap.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocPresenter;
import stroom.floormap.client.presenter.FloorMapEventStoreSettingsPresenter.FloorMapEventStoreSettingsView;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.component.client.Contexts;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

/// The settings tab for a [FloorMapEventStoreDoc].
///
/// Shows only what a user may safely change once data exists: how long an entity stays on the map,
/// how long data is kept, how large the store may grow, and whether to condense. The key and value
/// schemas are absent because the type fixes them — see the view template for why.
public class FloorMapEventStoreSettingsPresenter
        extends DocPresenter<FloorMapEventStoreSettingsView, FloorMapEventStoreDoc>
        implements DirtyUiHandlers {

    @Inject
    public FloorMapEventStoreSettingsPresenter(final EventBus eventBus,
                                               final FloorMapEventStoreSettingsView view) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);
        view.setUiHandlers(this);
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    protected void onRead(final DocRef docRef,
                          final FloorMapEventStoreDoc doc) {
        getView().setSettings(doc.getSettings());
        // The resolved value, not the raw one: an unset document shows 24 hours rather than an empty
        // box, and saving then writes that 24 hours down explicitly. Adopting the default on first
        // save is the intended behaviour - the alternative is a control that displays a number the
        // document does not hold.
        getView().setEventExpiry(doc.getEventExpiryOrDefault());
    }

    @Override
    protected FloorMapEventStoreDoc onWrite(final FloorMapEventStoreDoc doc) {
        return doc.copyEventStore()
                .settings(getView().getSettings())
                .eventExpiry(getView().getEventExpiry())
                .build();
    }

    public interface FloorMapEventStoreSettingsView
            extends View, HasUiHandlers<DirtyUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        AbstractPlanBSettings getSettings();

        void setSettings(AbstractPlanBSettings settings);

        SimpleDuration getEventExpiry();

        void setEventExpiry(SimpleDuration eventExpiry);
    }
}
