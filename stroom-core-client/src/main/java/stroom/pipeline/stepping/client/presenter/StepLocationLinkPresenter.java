/*
 * Copyright 2024 Crown Copyright
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
import stroom.pipeline.stepping.client.presenter.StepLocationLinkPresenter.StepLocationLinkView;
import stroom.widget.button.client.LinkButton;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

public class StepLocationLinkPresenter
        extends MyPresenterWidget<StepLocationLinkView> {

    private final StepLocationPresenter stepLocationPresenter;
    private StepLocation stepLocation;
    private boolean enabled = true;

    private Consumer<StepLocation> onStepLocation = stepLocation -> {
    };

    @Inject
    public StepLocationLinkPresenter(final EventBus eventBus,
                                     final StepLocationLinkView view,
                                     final StepLocationPresenter stepLocationPresenter) {
        super(eventBus, view);
        own(stepLocationPresenter);
        this.stepLocationPresenter = stepLocationPresenter;
        setStepLocation(new StepLocation(0L, 0L, 0L));
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().getButton().addClickHandler(event -> {
            if (!enabled) {
                return;
            }
            stepLocationPresenter.setStepLocation(stepLocation);
            ShowPopupEvent.builder(stepLocationPresenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .modal()
                    .caption("Set Location")
                    .onShow(e -> stepLocationPresenter.focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            onStepLocation.accept(stepLocationPresenter.getStepLocation());
                        }
                        e.hide();
                    })
                    .fire();
        });
    }

    /// Sets whether the location can be clicked to set a new one. It is enabled once a stream has
    /// been chosen to step through.
    ///
    /// @param enabled True to let the location be set.
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
        getView().setEnabled(enabled);
    }

    public void setStepLocation(final StepLocation stepLocation) {
        this.stepLocation = stepLocation;
        updateLabel(stepLocation);
    }

    private void updateLabel(final StepLocation stepLocation) {
        final LinkButton button = getView().getButton();
        if (stepLocation.getMetaId() == 0 && stepLocation.getPartIndex() == 0 && stepLocation.getRecordIndex() == 0) {
            button.setText("[??:??:??]");
            // The text is a code, so the name says what it is and what pressing it does
            button.setAriaLabel("Set Location ([??:??:??], none chosen)");
        } else {
            final String text = "[" +
                                stepLocation.getMetaId() + ":" +
                                (stepLocation.getPartIndex() + 1) + ":" +
                                (stepLocation.getRecordIndex() + 1) +
                                "]";
            button.setText(text);
            button.setAriaLabel("Set Location (" + text + ", stream " + stepLocation.getMetaId()
                                + ", part " + (stepLocation.getPartIndex() + 1)
                                + ", record " + (stepLocation.getRecordIndex() + 1) + ")");
        }
    }

    /// @param onStepLocation What to run with the location the user asks to step to.
    public void setOnStepLocation(final Consumer<StepLocation> onStepLocation) {
        this.onStepLocation = onStepLocation;
    }

    public interface StepLocationLinkView extends View {

        /// @return The button showing the location, which opens the dialog that sets it.
        LinkButton getButton();

        /// Shows whether the location can be clicked to set a new one.
        ///
        /// @param enabled True if the location can be set.
        void setEnabled(boolean enabled);
    }
}
