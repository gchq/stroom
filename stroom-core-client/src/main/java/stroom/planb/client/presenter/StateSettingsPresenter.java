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
import stroom.planb.client.presenter.StateSettingsPresenter.StateSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.StateSettings;
import stroom.util.shared.ModelStringUtil;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class StateSettingsPresenter
        extends AbstractPlanBSettingsPresenter<StateSettingsView> {

    @Inject
    public StateSettingsPresenter(
            final EventBus eventBus,
            final StateSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AbstractPlanBSettings settings, final boolean readOnly) {
        if (settings instanceof final StateSettings stateSettings) {
            setReadOnly(readOnly);
            if (stateSettings.getMaxStoreSize() == null) {
                getView().setMaxStoreSize("10 GB");
            } else {
                getView().setMaxStoreSize(ModelStringUtil.formatIECByteSizeString(
                        stateSettings.getMaxStoreSize(),
                        true,
                        ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES));
            }
            getView().setOverwrite(stateSettings.getOverwrite());
        }
    }

    public AbstractPlanBSettings write() {
        Long maxStoreSize = null;
        final String string = getView().getMaxStoreSize().trim();
        if (!string.isEmpty()) {
            maxStoreSize = ModelStringUtil.parseIECByteSizeString(string);
        }

        return StateSettings
                .builder()
                .maxStoreSize(maxStoreSize)
                .overwrite(getView().getOverwrite())
                .build();
    }

    public interface StateSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        String getMaxStoreSize();

        void setMaxStoreSize(String maxStoreSize);

        Boolean getOverwrite();

        void setOverwrite(Boolean overwrite);
    }
}
