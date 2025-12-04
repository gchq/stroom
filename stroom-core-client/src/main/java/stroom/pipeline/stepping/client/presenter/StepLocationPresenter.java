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

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class StepLocationPresenter
        extends MyPresenterWidget<StepLocationPresenter.StepLocationView>
        implements Focus {

    @Inject
    public StepLocationPresenter(final EventBus eventBus, final StepLocationView view) {
        super(eventBus, view);
    }

    public void setStepLocation(final StepLocation stepLocation) {
        getView().setStepLocation(stepLocation);
    }

    public StepLocation getStepLocation() {
        return getView().getStepLocation();
    }

    @Override
    public void focus() {
        getView().focus();
    }

    public interface StepLocationView extends View, Focus {

        StepLocation getStepLocation();

        void setStepLocation(StepLocation stepLocation);
    }
}
