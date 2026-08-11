/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.planb.client.presenter;

import stroom.document.client.event.ChangeUiHandlers;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.TraceSettingsPresenter.TraceSettingsView;
import stroom.planb.client.view.GeneralSettingsView;
import stroom.planb.client.view.RetentionSettingsView;
import stroom.planb.client.view.SharedFileStoreSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.TraceSettings;
import stroom.util.shared.time.SimpleDuration;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

/**
 * Settings for a Traces store. A trace store is only ever served from a shared file store, so this
 * offers no snapshot or part-transfer settings — see {@code AbstractHttpStoreSettings} for the store
 * types that do.
 */
public class TraceSettingsPresenter
        extends AbstractPlanBSettingsPresenter<TraceSettingsView> {

    /**
     * Held from the most recent {@link #read} so that {@link #write} round-trips it. There is no
     * editor for it, and without this a save would silently clear whatever was configured.
     */
    private SimpleDuration maxQueryTimeRange;

    @Inject
    public TraceSettingsPresenter(
            final EventBus eventBus,
            final TraceSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AbstractPlanBSettings settings, final boolean readOnly) {
        if (settings instanceof final TraceSettings traceSettings) {
            read(traceSettings, readOnly);
        } else {
            read(new TraceSettings.Builder().build(), readOnly);
        }
    }

    private void read(final TraceSettings settings, final boolean readOnly) {
        setReadOnly(readOnly);
        maxQueryTimeRange = settings.getMaxQueryTimeRange();
        getView().setMaxStoreSize(settings.getMaxStoreSize());
        getView().setMaxSpansPerTrace(settings.getMaxSpansPerTrace());
        getView().setRetention(settings.getRetention());
        getView().setSharedFileStore(settings.getSharedFileStore());
    }

    public AbstractPlanBSettings write() {
        return new TraceSettings.Builder()
                .maxStoreSize(getView().getMaxStoreSize())
                .maxSpansPerTrace(getView().getMaxSpansPerTrace())
                .retention(getView().getRetention())
                .sharedFileStore(getView().getSharedFileStore())
                .maxQueryTimeRange(maxQueryTimeRange)
                .build();
    }

    public interface TraceSettingsView extends
            View,
            GeneralSettingsView,
            SharedFileStoreSettingsView,
            RetentionSettingsView,
            ReadOnlyChangeHandler,
            HasUiHandlers<ChangeUiHandlers> {

        Long getMaxSpansPerTrace();

        void setMaxSpansPerTrace(Long maxSpansPerTrace);
    }
}
