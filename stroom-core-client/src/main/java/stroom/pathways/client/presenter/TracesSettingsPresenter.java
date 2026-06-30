/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.pathways.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.pathways.client.presenter.TracesSettingsPresenter.TracesSettingsView;
import stroom.pathways.shared.TracesDoc;
import stroom.planb.client.presenter.PlanBSettingsPresenter;
import stroom.planb.shared.StateType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

/**
 * Settings tab presenter for {@link TracesDoc}.
 *
 * <p>This is a thin adapter that satisfies the {@code DocPresenter<?, TracesDoc>}
 * contract required by {@link stroom.entity.client.presenter.DocTabProvider}.
 * All settings form logic is delegated to {@link PlanBSettingsPresenter}, which
 * is locked to {@link StateType#TRACE} so the state-type dropdown is hidden.
 *
 * <p>The {@code updateTraceSettingsForSharding} logic previously in this class has
 * moved to {@link stroom.planb.client.presenter.TraceSettingsPresenter#onChange()},
 * where it belongs alongside the sharding field state.
 */
public class TracesSettingsPresenter
        extends DocPresenter<TracesSettingsView, TracesDoc> {

    private final PlanBSettingsPresenter planBSettingsPresenter;

    @Inject
    public TracesSettingsPresenter(final EventBus eventBus,
                                   final TracesSettingsView view,
                                   final PlanBSettingsPresenter planBSettingsPresenter) {
        super(eventBus, view);
        this.planBSettingsPresenter = planBSettingsPresenter;
        planBSettingsPresenter.setStateTypeLocked(true);
        view.setSettingsView(planBSettingsPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(planBSettingsPresenter.addChangeHandler(this::onChange));
    }

    @Override
    protected void onRead(final DocRef docRef, final TracesDoc doc, final boolean readOnly) {
        getView().onReadOnly(readOnly);
        planBSettingsPresenter.readSettings(doc.getSettings(), StateType.TRACE, readOnly);
        final boolean locked = doc.hasSharedFileStoreData();
        planBSettingsPresenter.setShardCountLocked(locked);
        planBSettingsPresenter.setSharedPathLocked(locked);
    }

    @Override
    protected TracesDoc onWrite(final TracesDoc doc) {
        return doc.copyTraces()
                .stateType(StateType.TRACE)
                .settings(planBSettingsPresenter.writeSettings())
                .build();
    }

    public interface TracesSettingsView extends View, ReadOnlyChangeHandler {

        void setSettingsView(View view);
    }
}
