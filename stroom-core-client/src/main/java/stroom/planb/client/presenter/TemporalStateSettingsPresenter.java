/*
 * Copyright 2017-2024 Crown Copyright
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

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.TemporalStateSettingsPresenter.TemporalStateSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.SnapshotSettings;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.shared.ModelStringUtil;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class TemporalStateSettingsPresenter
        extends AbstractPlanBSettingsPresenter<TemporalStateSettingsView> {

    @Inject
    public TemporalStateSettingsPresenter(
            final EventBus eventBus,
            final TemporalStateSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AbstractPlanBSettings settings, final boolean readOnly) {
        if (settings instanceof final TemporalStateSettings temporalStateSettings) {
            read(temporalStateSettings, readOnly);
        } else {
            read(TemporalStateSettings.builder().build(), readOnly);
        }
    }

    private void read(final TemporalStateSettings settings, final boolean readOnly) {
        setReadOnly(readOnly);
        getView().setCondense(settings.getCondense());
        getView().setRetention(settings.getRetention());
        setMaxStoreSize(settings.getMaxStoreSize());
        getView().setSynchroniseMerge(settings.isSynchroniseMerge());
        getView().setOverwrite(settings.getOverwrite());

        final SnapshotSettings snapshotSettings = settings.getSnapshotSettings();
        if (snapshotSettings != null) {
            getView().setUseSnapshotsForLookup(snapshotSettings.isUseSnapshotsForLookup());
            getView().setUseSnapshotsForGet(snapshotSettings.isUseSnapshotsForGet());
            getView().setUseSnapshotsForQuery(snapshotSettings.isUseSnapshotsForQuery());
        }
    }

    private void setMaxStoreSize(Long size) {
        getView().setMaxStoreSize(ModelStringUtil.formatIECByteSizeString(
                size == null ? DEFAULT_MAX_STORE_SIZE : size,
                true,
                ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES));
    }

    public AbstractPlanBSettings write() {
        final SnapshotSettings snapshotSettings = new SnapshotSettings(
                getView().isUseSnapshotsForLookup(),
                getView().isUseSnapshotsForGet(),
                getView().isUseSnapshotsForQuery());

        return TemporalStateSettings
                .builder()
                .condense(getView().getCondense())
                .retention(getView().getRetention())
                .maxStoreSize(getMaxStoreSize())
                .synchroniseMerge(getView().getSynchroniseMerge())
                .overwrite(getView().getOverwrite())
                .snapshotSettings(snapshotSettings)
                .build();
    }

    private Long getMaxStoreSize() {
        try {
            final String string = getView().getMaxStoreSize().trim();
            if (!string.isEmpty()) {
                return ModelStringUtil.parseIECByteSizeString(string);
            }
        } catch (final RuntimeException e) {
            // Ignore.
        }
        setMaxStoreSize(DEFAULT_MAX_STORE_SIZE);
        return DEFAULT_MAX_STORE_SIZE;
    }

    public interface TemporalStateSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        DurationSetting getCondense();

        void setCondense(DurationSetting condense);

        DurationSetting getRetention();

        void setRetention(DurationSetting retention);

        String getMaxStoreSize();

        void setMaxStoreSize(String maxStoreSize);

        boolean getSynchroniseMerge();

        void setSynchroniseMerge(boolean synchroniseMerge);

        Boolean getOverwrite();

        void setOverwrite(Boolean overwrite);

        boolean isUseSnapshotsForLookup();

        void setUseSnapshotsForLookup(boolean useSnapshotsForLookup);

        boolean isUseSnapshotsForGet();

        void setUseSnapshotsForGet(boolean useSnapshotsForGet);

        boolean isUseSnapshotsForQuery();

        void setUseSnapshotsForQuery(boolean useSnapshotsForQuery);
    }
}
