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

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class StepControlEvent extends GwtEvent<StepControlEvent.StepControlHandler> {

    private static final Type<StepControlHandler> TYPE = new Type<>();
    private final StepType stepType;
    private final StepLocation stepLocation;

    private StepControlEvent(final StepType stepType, final StepLocation stepLocation) {
        this.stepType = stepType;
        this.stepLocation = stepLocation;
    }

    public static <I> void fire(final HasHandlers source, final StepType stepType, final StepLocation stepLocation) {
        final StepControlEvent event = new StepControlEvent(stepType, stepLocation);
        source.fireEvent(event);
    }

    public static <I> void fire(final HasHandlers source, final StepType stepType) {
        final StepControlEvent event = new StepControlEvent(stepType, null);
        source.fireEvent(event);
    }

    public static Type<StepControlHandler> getType() {
        return TYPE;
    }

    @Override
    public final Type<StepControlHandler> getAssociatedType() {
        return TYPE;
    }

    public StepType getStepType() {
        return stepType;
    }

    public StepLocation getStepLocation() {
        return stepLocation;
    }

    @Override
    protected void dispatch(final StepControlHandler handler) {
        handler.onSelection(this);
    }

    public interface StepControlHandler extends EventHandler {

        void onSelection(StepControlEvent event);
    }
}
