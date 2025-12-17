/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.editor.client.event.ChangeFilterEvent;
import stroom.editor.client.event.ChangeFilterEvent.ChangeFilterHandler;
import stroom.editor.client.event.HasChangeFilterHandlers;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.presenter.StepControlEvent.StepControlHandler;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class StepControlPresenter
        extends MyPresenterWidget<StepControlPresenter.StepControlView>
        implements StepControlUIHandlers,
        HasChangeFilterHandlers {

    private StepLocation endLocation = null;

    @Inject
    public StepControlPresenter(final EventBus eventBus, final StepControlView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    @Override
    public void filter() {
        ChangeFilterEvent.fire(this);
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

    public void step(final StepType stepType) {
        //noinspection EnhancedSwitchMigration // cos GWT
        switch (stepType) {
            case FIRST:
                stepFirst();
                break;
            case BACKWARD:
                stepBackward();
                break;
            case FORWARD:
                stepForward();
                break;
            case LAST:
                stepLast();
                break;
            case REFRESH:
                stepRefresh();
                break;
            default:
                throw new RuntimeException("Unknown type " + stepType);
        }
    }

    public boolean isEnabled(final StepType stepType) {
        return getView().isEnabled(stepType);
    }

    public void setEnabledButtons(final boolean justStepped,
                                  final StepType stepType,
                                  final boolean showingData,
                                  final boolean foundRecord,
                                  final boolean hasFatal,
                                  final boolean isFiltered,
                                  final StepLocation stepLocation) {

        // See if we are on the first record
        final boolean isFirstRecord = stepLocation != null
                && stepLocation.getPartIndex() == 0
                && stepLocation.getRecordIndex() == 0;
        // If a filter is in place the end record is a variable concept
        final boolean isLastRecord = !isFiltered
                && !hasFatal // if hasFatal then
                && stepLocation != null
                && endLocation != null
                && stepLocation.getPartIndex() == endLocation.getPartIndex()
                && stepLocation.getRecordIndex() == endLocation.getRecordIndex();
        final boolean canStepFurtherBack = foundRecord && !hasFatal && !isFirstRecord;
        final boolean canStepFurtherForward = foundRecord && !hasFatal && !isLastRecord;

        if (justStepped) {
            if (stepType == StepType.FIRST) {
                getView().setStepFirstEnabled(false);
                getView().setStepBackwardEnabled(false);
                getView().setStepForwardEnabled(true);
                getView().setStepLastEnabled(true);
            } else if (stepType == StepType.BACKWARD) {
                getView().setStepFirstEnabled(canStepFurtherBack);
                getView().setStepBackwardEnabled(canStepFurtherBack);
                getView().setStepForwardEnabled(true);
                getView().setStepLastEnabled(true);
            } else if (stepType == StepType.FORWARD) {
                markEndLocation(foundRecord, hasFatal, isFiltered, stepLocation);
                getView().setStepFirstEnabled(true);
                getView().setStepBackwardEnabled(true);
                getView().setStepForwardEnabled(canStepFurtherForward);
                getView().setStepLastEnabled(canStepFurtherForward);
            } else if (stepType == StepType.LAST) {
                markEndLocation(foundRecord, hasFatal, isFiltered, stepLocation);
                getView().setStepFirstEnabled(true);
                getView().setStepBackwardEnabled(true);
                getView().setStepForwardEnabled(false);
                getView().setStepLastEnabled(false);
            } else if (stepType == StepType.REFRESH) {
                getView().setStepFirstEnabled(!isFirstRecord);
                getView().setStepBackwardEnabled(!isFirstRecord);
                getView().setStepForwardEnabled(!isLastRecord);
                getView().setStepLastEnabled(!isLastRecord);
            }
        } else {
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
        }
        getView().setStepRefreshEnabled(showingData);
    }

    private void markEndLocation(final boolean foundRecord,
                                 final boolean hasFatal,
                                 final boolean isFiltered,
                                 final StepLocation stepLocation) {
        // Record the end location for future now that we know it.
        if (endLocation == null && !isFiltered && !hasFatal && !foundRecord) {
            endLocation = stepLocation;
        }
    }

    @Override
    public HandlerRegistration addChangeFilterHandler(final ChangeFilterHandler handler) {
        return addHandlerToSource(ChangeFilterEvent.TYPE, handler);
    }

    public HandlerRegistration addStepControlHandler(final StepControlHandler handler) {
        return addHandlerToSource(StepControlEvent.getType(), handler);
    }


    // --------------------------------------------------------------------------------


    public interface StepControlView extends View, HasUiHandlers<StepControlUIHandlers> {

        void setStepFirstEnabled(boolean enabled);

        void setStepBackwardEnabled(boolean enabled);

        void setStepForwardEnabled(boolean enabled);

        void setStepLastEnabled(boolean enabled);

        void setStepRefreshEnabled(boolean enabled);

        boolean isEnabled(final StepType stepType);
    }
}
