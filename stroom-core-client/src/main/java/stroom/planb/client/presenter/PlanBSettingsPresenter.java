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
import stroom.planb.client.view.CondenseSettingsView;
import stroom.planb.client.view.GeneralSettingsView;
import stroom.planb.client.view.RetentionSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.DurationSetting;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RetentionSettings;
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
    private final Provider<RangeStateSettingsPresenter> rangeStateSettingsPresenterProvider;
    private final Provider<TemporalRangeStateSettingsPresenter> temporalRangeStateSettingsPresenterProvider;
    private final Provider<SessionSettingsPresenter> sessionSettingsPresenterProvider;
    private final Provider<HistogramSettingsPresenter> histogramSettingsPresenterProvider;
    private final Provider<MetricSettingsPresenter> metricSettingsPresenterProvider;
    private final Provider<TraceSettingsPresenter> traceSettingsPresenterProvider;

    private AbstractPlanBSettingsPresenter<?> settingsPresenter;
    private StateType currentStateType;

    private Long maxStoreSize;
    private Boolean synchroniseMerge;
    private Boolean overwrite;
    private DurationSetting condense;
    private RetentionSettings retention;

    @Inject
    public PlanBSettingsPresenter(
            final EventBus eventBus,
            final PlanBSettingsView view,
            final Provider<StateSettingsPresenter> stateSettingsPresenterProvider,
            final Provider<TemporalStateSettingsPresenter> temporalStateSettingsPresenterProvider,
            final Provider<RangeStateSettingsPresenter> rangeStateSettingsPresenterProvider,
            final Provider<TemporalRangeStateSettingsPresenter> temporalRangeStateSettingsPresenterProvider,
            final Provider<SessionSettingsPresenter> sessionSettingsPresenterProvider,
            final Provider<HistogramSettingsPresenter> histogramSettingsPresenterProvider,
            final Provider<MetricSettingsPresenter> metricSettingsPresenterProvider,
            final Provider<TraceSettingsPresenter> traceSettingsPresenterProvider) {
        super(eventBus, view);
        this.stateSettingsPresenterProvider = stateSettingsPresenterProvider;
        this.temporalStateSettingsPresenterProvider = temporalStateSettingsPresenterProvider;
        this.rangeStateSettingsPresenterProvider = rangeStateSettingsPresenterProvider;
        this.temporalRangeStateSettingsPresenterProvider = temporalRangeStateSettingsPresenterProvider;
        this.sessionSettingsPresenterProvider = sessionSettingsPresenterProvider;
        this.histogramSettingsPresenterProvider = histogramSettingsPresenterProvider;
        this.metricSettingsPresenterProvider = metricSettingsPresenterProvider;
        this.traceSettingsPresenterProvider = traceSettingsPresenterProvider;
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
        if (settingsPresenter != null) {
            maxStoreSize = getMaxStoreSize(settingsPresenter.getView());
            synchroniseMerge = getSynchroniseMerge(settingsPresenter.getView());
            overwrite = getOverwrite(settingsPresenter.getView());
            condense = getCondense(settingsPresenter.getView());
            retention = getRetention(settingsPresenter.getView());
        }

        final StateType stateType = getView().getStateType();
        switch (stateType) {
            case STATE: {
                final StateSettingsPresenter presenter =
                        stateSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setSynchroniseMerge(synchroniseMerge);
                presenter.getView().setOverwrite(overwrite);
                presenter.getView().setRetention(retention);
                settingsPresenter = presenter;
                break;
            }
            case TEMPORAL_STATE: {
                final TemporalStateSettingsPresenter presenter =
                        temporalStateSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setSynchroniseMerge(synchroniseMerge);
                presenter.getView().setOverwrite(overwrite);
                presenter.getView().setCondense(condense);
                presenter.getView().setRetention(retention);
                settingsPresenter = presenter;
                break;
            }
            case RANGED_STATE: {
                final RangeStateSettingsPresenter presenter =
                        rangeStateSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setSynchroniseMerge(synchroniseMerge);
                presenter.getView().setOverwrite(overwrite);
                presenter.getView().setRetention(retention);
                settingsPresenter = presenter;
                break;
            }
            case TEMPORAL_RANGED_STATE: {
                final TemporalRangeStateSettingsPresenter presenter =
                        temporalRangeStateSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setSynchroniseMerge(synchroniseMerge);
                presenter.getView().setOverwrite(overwrite);
                presenter.getView().setCondense(condense);
                presenter.getView().setRetention(retention);
                settingsPresenter = presenter;
                break;
            }
            case SESSION: {
                final SessionSettingsPresenter presenter =
                        sessionSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setSynchroniseMerge(synchroniseMerge);
                presenter.getView().setOverwrite(overwrite);
                presenter.getView().setCondense(condense);
                presenter.getView().setRetention(retention);
                settingsPresenter = presenter;
                break;
            }
            case HISTOGRAM: {
                final HistogramSettingsPresenter presenter =
                        histogramSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setSynchroniseMerge(synchroniseMerge);
                presenter.getView().setOverwrite(overwrite);
                presenter.getView().setRetention(retention);
                settingsPresenter = presenter;
                break;
            }
            case METRIC: {
                final MetricSettingsPresenter presenter =
                        metricSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setSynchroniseMerge(synchroniseMerge);
                presenter.getView().setOverwrite(overwrite);
                presenter.getView().setRetention(retention);
                settingsPresenter = presenter;
                break;
            }
            case TRACE: {
                final TraceSettingsPresenter presenter =
                        traceSettingsPresenterProvider.get();
                presenter.getView().setMaxStoreSize(maxStoreSize);
                presenter.getView().setSynchroniseMerge(synchroniseMerge);
                presenter.getView().setOverwrite(overwrite);
                presenter.getView().setRetention(retention);
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

    private Long getMaxStoreSize(final View view) {
        if (view instanceof final GeneralSettingsView generalSettingsView) {
            return generalSettingsView.getMaxStoreSize();
        }
        return maxStoreSize;
    }

    private Boolean getSynchroniseMerge(final View view) {
        if (view instanceof final GeneralSettingsView generalSettingsView) {
            return generalSettingsView.getSynchroniseMerge();
        }
        return synchroniseMerge;
    }

    private Boolean getOverwrite(final View view) {
        if (view instanceof final GeneralSettingsView generalSettingsView) {
            return generalSettingsView.getOverwrite();
        }
        return overwrite;
    }

    private DurationSetting getCondense(final View view) {
        if (view instanceof final CondenseSettingsView condenseSettingsView) {
            return condenseSettingsView.getCondense();
        }
        return condense;
    }

    private RetentionSettings getRetention(final View view) {
        if (view instanceof final RetentionSettingsView retentionSettingsView) {
            return retentionSettingsView.getRetention();
        }
        return retention;
    }

    public interface PlanBSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        StateType getStateType();

        void setStateType(StateType stateType);

        void setSettingsView(View view);
    }
}
