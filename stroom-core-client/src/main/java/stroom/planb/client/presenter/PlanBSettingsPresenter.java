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

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.PlanBSettingsPresenter.PlanBSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;

public class PlanBSettingsPresenter
        extends DocumentEditPresenter<PlanBSettingsView, PlanBDoc>
        implements PlanBSettingsUiHandlers {

    private final Provider<StateSettingsPresenter> stateSettingsPresenterProvider;
    private final Provider<TemporalStateSettingsPresenter> temporalStateSettingsPresenterProvider;
    private final Provider<RangedStateSettingsPresenter> rangedStateSettingsPresenterProvider;
    private final Provider<TemporalRangedStateSettingsPresenter> temporalRangedStateSettingsPresenterProvider;
    private final Provider<SessionSettingsPresenter> sessionSettingsPresenterProvider;

    private AbstractPlanBSettingsPresenter<?> settingsPresenter;
    private StateType currentStateType;

    @Inject
    public PlanBSettingsPresenter(
            final EventBus eventBus,
            final PlanBSettingsView view,
            final Provider<StateSettingsPresenter> stateSettingsPresenterProvider,
            final Provider<TemporalStateSettingsPresenter> temporalStateSettingsPresenterProvider,
            final Provider<RangedStateSettingsPresenter> rangedStateSettingsPresenterProvider,
            final Provider<TemporalRangedStateSettingsPresenter> temporalRangedStateSettingsPresenterProvider,
            final Provider<SessionSettingsPresenter> sessionSettingsPresenterProvider) {
        super(eventBus, view);
        this.stateSettingsPresenterProvider = stateSettingsPresenterProvider;
        this.temporalStateSettingsPresenterProvider = temporalStateSettingsPresenterProvider;
        this.rangedStateSettingsPresenterProvider = rangedStateSettingsPresenterProvider;
        this.temporalRangedStateSettingsPresenterProvider = temporalRangedStateSettingsPresenterProvider;
        this.sessionSettingsPresenterProvider = sessionSettingsPresenterProvider;
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        setDirty(true);
        changeStateType();
    }

    @Override
    protected void onRead(final DocRef docRef, final PlanBDoc doc, final boolean readOnly) {
        getView().onReadOnly(readOnly);
        getView().setStateType(doc.getStateType());
        changeStateType();
    }

    @Override
    protected PlanBDoc onWrite(final PlanBDoc doc) {
        AbstractPlanBSettings settings = null;
        if (settingsPresenter != null) {
            settings = settingsPresenter.write();
        }
        return doc.copy().stateType(getView().getStateType()).settings(settings).build();
    }

    private void changeStateType() {
        final StateType stateType = getView().getStateType();
        if (!Objects.equals(stateType, currentStateType)) {
            switch (stateType) {
                case STATE: {
                    settingsPresenter = stateSettingsPresenterProvider.get();
                    break;
                }
                case TEMPORAL_STATE: {
                    settingsPresenter = temporalStateSettingsPresenterProvider.get();
                    break;
                }
                case RANGED_STATE: {
                    settingsPresenter = rangedStateSettingsPresenterProvider.get();
                    break;
                }
                case TEMPORAL_RANGED_STATE: {
                    settingsPresenter = temporalRangedStateSettingsPresenterProvider.get();
                    break;
                }
                case SESSION: {
                    settingsPresenter = sessionSettingsPresenterProvider.get();
                    break;
                }
            }
            if (settingsPresenter != null) {
                settingsPresenter.read(getEntity().getSettings(), isReadOnly());
                getView().setSettingsView(settingsPresenter.getView());
            }
            currentStateType = stateType;
        }
    }

    public interface PlanBSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        StateType getStateType();

        void setStateType(StateType stateType);

        void setSettingsView(View view);
    }
}
