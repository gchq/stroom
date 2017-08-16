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
import stroom.pipeline.shared.StepType;
import stroom.pipeline.stepping.client.presenter.StepControlEvent.StepControlHandler;

public class StepControlPresenter extends MyPresenterWidget<StepControlPresenter.StepControlView>
        implements StepControlUIHandlers {
    @Inject
    public StepControlPresenter(final EventBus eventBus, final StepControlView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void stepFirst() {
        StepControlEvent.fire(this, StepType.FIRST);
    }

    @Override
    public void stepBackward() {
        StepControlEvent.fire(this, StepType.BACKWARD);
    }

    @Override
    public void stepForward() {
        StepControlEvent.fire(this, StepType.FORWARD);
    }

    @Override
    public void stepLast() {
        StepControlEvent.fire(this, StepType.LAST);
    }

    @Override
    public void stepRefresh() {
        StepControlEvent.fire(this, StepType.REFRESH);
    }

    public void setEnabledButtons(final boolean justStepped, final StepType stepType, final boolean tasksSelected,
                                  final boolean showingData, final boolean foundRecord) {
        if (justStepped) {
            if (stepType == StepType.FIRST) {
                getView().setStepFirstEnabled(false);
                getView().setStepBackwardEnabled(false);
                getView().setStepForwardEnabled(true);
                getView().setStepLastEnabled(true);
            } else if (stepType == StepType.BACKWARD) {
                getView().setStepFirstEnabled(foundRecord);
                getView().setStepBackwardEnabled(foundRecord);
                getView().setStepForwardEnabled(true);
                getView().setStepLastEnabled(true);
            } else if (stepType == StepType.FORWARD) {
                getView().setStepFirstEnabled(true);
                getView().setStepBackwardEnabled(true);
                getView().setStepForwardEnabled(foundRecord);
                getView().setStepLastEnabled(foundRecord);
            } else if (stepType == StepType.LAST) {
                getView().setStepFirstEnabled(true);
                getView().setStepBackwardEnabled(true);
                getView().setStepForwardEnabled(false);
                getView().setStepLastEnabled(false);
            }
            getView().setStepRefreshEnabled(showingData);

        } else if (tasksSelected) {
            if (stepType == null || stepType == StepType.BACKWARD) {
                getView().setStepFirstEnabled(showingData);
                getView().setStepBackwardEnabled(showingData);
                getView().setStepForwardEnabled(true);
                getView().setStepLastEnabled(true);
            } else if (stepType == StepType.FORWARD) {
                getView().setStepFirstEnabled(true);
                getView().setStepBackwardEnabled(true);
                getView().setStepForwardEnabled(showingData);
                getView().setStepLastEnabled(showingData);
            } else {
                getView().setStepFirstEnabled(showingData);
                getView().setStepBackwardEnabled(showingData);
                getView().setStepForwardEnabled(showingData);
                getView().setStepLastEnabled(showingData);
            }
            getView().setStepRefreshEnabled(showingData);

        } else {
            // There are no tasks selected so disable the buttons.
            getView().setStepFirstEnabled(false);
            getView().setStepBackwardEnabled(false);
            getView().setStepForwardEnabled(false);
            getView().setStepLastEnabled(false);
            getView().setStepRefreshEnabled(showingData);
        }
    }

    public HandlerRegistration addStepControlHandler(final StepControlHandler handler) {
        return addHandlerToSource(StepControlEvent.getType(), handler);
    }

    public interface StepControlView extends View, HasUiHandlers<StepControlUIHandlers> {
        void setStepFirstEnabled(boolean enabled);

        void setStepBackwardEnabled(boolean enabled);

        void setStepForwardEnabled(boolean enabled);

        void setStepLastEnabled(boolean enabled);

        void setStepRefreshEnabled(boolean enabled);
    }
}
