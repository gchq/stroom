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
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

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

    private String maxStoreSize;
    private DurationSetting condense;
    private DurationSetting retention;
    private Boolean overwrite;

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
        currentStateType = null;
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
        maxStoreSize = getMaxStoreSize();
        condense = getCondense();
        retention = getRetention();
        overwrite = getOverwrite();

        final StateType stateType = getView().getStateType();
        switch (stateType) {
            case STATE: {
                final StateSettingsPresenter presenter =
                        stateSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setOverwrite(overwrite);
                settingsPresenter = presenter;
                break;
            }
            case TEMPORAL_STATE: {
                final TemporalStateSettingsPresenter presenter =
                        temporalStateSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setCondense(condense);
                presenter.getView().setRetention(retention);
                presenter.getView().setOverwrite(overwrite);
                settingsPresenter = presenter;
                break;
            }
            case RANGED_STATE: {
                final RangedStateSettingsPresenter presenter =
                        rangedStateSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setOverwrite(overwrite);
                settingsPresenter = presenter;
                break;
            }
            case TEMPORAL_RANGED_STATE: {
                final TemporalRangedStateSettingsPresenter presenter =
                        temporalRangedStateSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setCondense(condense);
                presenter.getView().setRetention(retention);
                presenter.getView().setOverwrite(overwrite);
                settingsPresenter = presenter;
                break;
            }
            case SESSION: {
                final SessionSettingsPresenter presenter =
                        sessionSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setCondense(condense);
                presenter.getView().setRetention(retention);
                presenter.getView().setOverwrite(overwrite);
                settingsPresenter = presenter;
                break;
            }
        }

        if (settingsPresenter != null) {
            if (currentStateType == null) {
                settingsPresenter.read(getEntity().getSettings(), isReadOnly());
            }
            getView().setSettingsView(settingsPresenter.getView());
            settingsPresenter.addDirtyHandler(e -> setDirty(true));
        }
        currentStateType = stateType;
    }

    private String getMaxStoreSize() {
        if (settingsPresenter instanceof
                final StateSettingsPresenter stateSettingsPresenter) {
            return stateSettingsPresenter.getView().getMaxStoreSize();
        } else if (settingsPresenter instanceof
                final TemporalStateSettingsPresenter temporalStateSettingsPresenter) {
            return temporalStateSettingsPresenter.getView().getMaxStoreSize();
        } else if (settingsPresenter instanceof
                final RangedStateSettingsPresenter rangedStateSettingsPresenter) {
            return rangedStateSettingsPresenter.getView().getMaxStoreSize();
        } else if (settingsPresenter instanceof
                final TemporalRangedStateSettingsPresenter temporalRangedStateSettingsPresenter) {
            return temporalRangedStateSettingsPresenter.getView().getMaxStoreSize();
        } else if (settingsPresenter instanceof
                final SessionSettingsPresenter sessionSettingsPresenter) {
            return sessionSettingsPresenter.getView().getMaxStoreSize();
        }
        return maxStoreSize;
    }

    private DurationSetting getCondense() {
        if (settingsPresenter instanceof
                final TemporalStateSettingsPresenter temporalStateSettingsPresenter) {
            return temporalStateSettingsPresenter.getView().getCondense();
        } else if (settingsPresenter instanceof
                final TemporalRangedStateSettingsPresenter temporalRangedStateSettingsPresenter) {
            return temporalRangedStateSettingsPresenter.getView().getCondense();
        } else if (settingsPresenter instanceof
                final SessionSettingsPresenter sessionSettingsPresenter) {
            return sessionSettingsPresenter.getView().getCondense();
        }
        return condense;
    }

    private DurationSetting getRetention() {
        if (settingsPresenter instanceof
                final TemporalStateSettingsPresenter temporalStateSettingsPresenter) {
            return temporalStateSettingsPresenter.getView().getRetention();
        } else if (settingsPresenter instanceof
                final TemporalRangedStateSettingsPresenter temporalRangedStateSettingsPresenter) {
            return temporalRangedStateSettingsPresenter.getView().getRetention();
        } else if (settingsPresenter instanceof
                final SessionSettingsPresenter sessionSettingsPresenter) {
            return sessionSettingsPresenter.getView().getRetention();
        }
        return retention;
    }

    private Boolean getOverwrite() {
        if (settingsPresenter instanceof
                final StateSettingsPresenter stateSettingsPresenter) {
            return stateSettingsPresenter.getView().getOverwrite();
        } else if (settingsPresenter instanceof
                final TemporalStateSettingsPresenter temporalStateSettingsPresenter) {
            return temporalStateSettingsPresenter.getView().getOverwrite();
        } else if (settingsPresenter instanceof
                final RangedStateSettingsPresenter rangedStateSettingsPresenter) {
            return rangedStateSettingsPresenter.getView().getOverwrite();
        } else if (settingsPresenter instanceof
                final TemporalRangedStateSettingsPresenter temporalRangedStateSettingsPresenter) {
            return temporalRangedStateSettingsPresenter.getView().getOverwrite();
        } else if (settingsPresenter instanceof
                final SessionSettingsPresenter sessionSettingsPresenter) {
            return sessionSettingsPresenter.getView().getOverwrite();
        }
        return overwrite;
    }

    public interface PlanBSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        StateType getStateType();

        void setStateType(StateType stateType);

        void setSettingsView(View view);
    }
}
