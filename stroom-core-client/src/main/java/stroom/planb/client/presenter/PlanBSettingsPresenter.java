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
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateType;
import stroom.util.shared.time.TimeUnit;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class PlanBSettingsPresenter
        extends DocumentEditPresenter<PlanBSettingsView, PlanBDoc>
        implements PlanBSettingsUiHandlers {


    @Inject
    public PlanBSettingsPresenter(
            final EventBus eventBus,
            final PlanBSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void onChange() {
        setDirty(true);
    }

    @Override
    protected void onRead(final DocRef docRef, final PlanBDoc doc, final boolean readOnly) {
        getView().onReadOnly(readOnly);
        getView().setStateType(doc.getStateType());
        getView().setCondense(doc.isCondense());
        getView().setCondenseAge(doc.getCondenseAge());
        getView().setCondenseTimeUnit(doc.getCondenseTimeUnit());
        getView().setRetainForever(doc.isRetainForever());
        getView().setRetainAge(doc.getRetainAge());
        getView().setRetainTimeUnit(doc.getRetainTimeUnit());
    }

    @Override
    protected PlanBDoc onWrite(final PlanBDoc doc) {
        doc.setStateType(getView().getStateType());
        doc.setCondense(getView().isCondense());
        doc.setCondenseAge(getView().getCondenseAge());
        doc.setCondenseTimeUnit(getView().getCondenseTimeUnit());
        doc.setRetainForever(getView().isRetainForever());
        doc.setRetainAge(getView().getRetainAge());
        doc.setRetainTimeUnit(getView().getRetainTimeUnit());
        return doc;
    }


    // --------------------------------------------------------------------------------


    public interface PlanBSettingsView
            extends View, ReadOnlyChangeHandler, HasUiHandlers<PlanBSettingsUiHandlers> {

        StateType getStateType();

        void setStateType(StateType stateType);

        boolean isCondense();

        void setCondense(boolean condense);

        int getCondenseAge();

        void setCondenseAge(int age);

        TimeUnit getCondenseTimeUnit();

        void setCondenseTimeUnit(TimeUnit condenseTimeUnit);

        boolean isRetainForever();

        void setRetainForever(boolean retainForever);

        int getRetainAge();

        void setRetainAge(int age);

        TimeUnit getRetainTimeUnit();

        void setRetainTimeUnit(TimeUnit retainTimeUnit);
    }
}
