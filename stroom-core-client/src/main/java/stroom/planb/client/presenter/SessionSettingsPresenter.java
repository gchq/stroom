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
import stroom.planb.client.presenter.SessionSettingsPresenter.SessionSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.SessionSettings;
import stroom.util.shared.ModelStringUtil;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class SessionSettingsPresenter
        extends AbstractPlanBSettingsPresenter<SessionSettingsView> {

    @Inject
    public SessionSettingsPresenter(
            final EventBus eventBus,
            final SessionSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AbstractPlanBSettings settings, final boolean readOnly) {
        if (settings instanceof final SessionSettings sessionSettings) {
            read(sessionSettings, readOnly);
        } else {
            read(SessionSettings.builder().build(), readOnly);
        }
    }

    private void read(final SessionSettings settings, final boolean readOnly) {
        setReadOnly(readOnly);
        getView().setCondense(settings.getCondense());
        getView().setRetention(settings.getRetention());
        setMaxStoreSize(settings.getMaxStoreSize());
        getView().setOverwrite(settings.getOverwrite());
    }

    private void setMaxStoreSize(Long size) {
        getView().setMaxStoreSize(ModelStringUtil.formatIECByteSizeString(
                size == null ? DEFAULT_MAX_STORE_SIZE : size,
                true,
                ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES));
    }

    public AbstractPlanBSettings write() {
        return SessionSettings
                .builder()
                .condense(getView().getCondense())
                .retention(getView().getRetention())
                .maxStoreSize(getMaxStoreSize())
                .overwrite(getView().getOverwrite())
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

    public interface SessionSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        DurationSetting getCondense();

        void setCondense(DurationSetting condense);

        DurationSetting getRetention();

        void setRetention(DurationSetting retention);

        String getMaxStoreSize();

        void setMaxStoreSize(String maxStoreSize);

        Boolean getOverwrite();

        void setOverwrite(Boolean overwrite);
    }
}
