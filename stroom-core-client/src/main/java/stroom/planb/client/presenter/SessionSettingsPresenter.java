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
            setReadOnly(readOnly);
            getView().setCondense(sessionSettings.getCondense());
            getView().setRetain(sessionSettings.getRetain());
            if (sessionSettings.getMaxStoreSize() == null) {
                getView().setMaxStoreSize("10 GB");
            } else {
                getView().setMaxStoreSize(ModelStringUtil.formatIECByteSizeString(
                        sessionSettings.getMaxStoreSize(),
                        true,
                        ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES));
            }
            getView().setOverwrite(sessionSettings.getOverwrite());
        }
    }

    public AbstractPlanBSettings write() {
        Long maxStoreSize = null;
        final String string = getView().getMaxStoreSize().trim();
        if (!string.isEmpty()) {
            maxStoreSize = ModelStringUtil.parseIECByteSizeString(string);
        }

        return SessionSettings
                .builder()
                .condense(getView().getCondense())
                .retain(getView().getRetain())
                .maxStoreSize(maxStoreSize)
                .overwrite(getView().getOverwrite())
                .build();
    }

    public interface SessionSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        DurationSetting getCondense();

        void setCondense(DurationSetting condense);

        DurationSetting getRetain();

        void setRetain(DurationSetting retain);

        String getMaxStoreSize();

        void setMaxStoreSize(String maxStoreSize);

        Boolean getOverwrite();

        void setOverwrite(Boolean overwrite);
    }
}
