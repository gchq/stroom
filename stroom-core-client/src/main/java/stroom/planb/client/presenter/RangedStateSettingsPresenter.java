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
import stroom.planb.client.presenter.RangedStateSettingsPresenter.RangedStateSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.RangedStateSettings;
import stroom.planb.shared.SnapshotSettings;
import stroom.util.shared.ModelStringUtil;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class RangedStateSettingsPresenter
        extends AbstractPlanBSettingsPresenter<RangedStateSettingsView> {

    @Inject
    public RangedStateSettingsPresenter(
            final EventBus eventBus,
            final RangedStateSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AbstractPlanBSettings settings, final boolean readOnly) {
        if (settings instanceof final RangedStateSettings rangedStateSettings) {
            read(rangedStateSettings, readOnly);
        } else {
            read(RangedStateSettings.builder().build(), readOnly);
        }
    }

    private void read(final RangedStateSettings settings, final boolean readOnly) {
        setReadOnly(readOnly);
        setMaxStoreSize(settings.getMaxStoreSize());
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

        return RangedStateSettings
                .builder()
                .maxStoreSize(getMaxStoreSize())
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

    public interface RangedStateSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        String getMaxStoreSize();

        void setMaxStoreSize(String maxStoreSize);

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
