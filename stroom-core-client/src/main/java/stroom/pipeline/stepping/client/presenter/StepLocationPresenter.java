/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.stepping.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.pipeline.shared.StepLocation;
import stroom.pipeline.shared.StepType;
import stroom.pipeline.stepping.client.presenter.StepControlEvent.StepControlHandler;

public class StepLocationPresenter extends MyPresenterWidget<StepLocationPresenter.StepLocationView>
        implements StepLocationUIHandlers {
    public interface StepLocationView extends View, HasUiHandlers<StepLocationUIHandlers> {
        void setStepLocation(StepLocation stepLocation);
    }

    @Inject
    public StepLocationPresenter(final EventBus eventBus, final StepLocationView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void changeLocation(final StepLocation stepLocation) {
        StepControlEvent.fire(this, StepType.REFRESH, stepLocation);
    }

    public void setStepLocation(final StepLocation stepLocation) {
        getView().setStepLocation(stepLocation);
    }

    public HandlerRegistration addStepControlHandler(final StepControlHandler handler) {
        return addHandlerToSource(StepControlEvent.getType(), handler);
    }
}
