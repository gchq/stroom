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
import stroom.pipeline.stepping.client.presenter.StepControlEvent.StepControlHandler;
import stroom.pipeline.stepping.client.presenter.StepLocationLinkPresenter.StepLocationLinkView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Label;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class StepLocationLinkPresenter
        extends MyPresenterWidget<StepLocationLinkView> {

    private final StepLocationPresenter stepLocationPresenter;
    private StepLocation stepLocation;

    @Inject
    public StepLocationLinkPresenter(final EventBus eventBus,
                                     final StepLocationLinkView view,
                                     final StepLocationPresenter stepLocationPresenter) {
        super(eventBus, view);
        this.stepLocationPresenter = stepLocationPresenter;
        setStepLocation(new StepLocation(0, 0, 0));
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().getLabel().addClickHandler(event -> {
            stepLocationPresenter.setStepLocation(stepLocation);
            ShowPopupEvent.builder(stepLocationPresenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .caption("Set Location")
                    .onShow(e -> stepLocationPresenter.focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            StepControlEvent.fire(this,
                                    StepType.REFRESH,
                                    stepLocationPresenter.getStepLocation());
                        }
                        e.hide();
                    })
                    .fire();
        });
    }

    public void setStepLocation(final StepLocation stepLocation) {
        this.stepLocation = stepLocation;
        updateLabel(stepLocation);
    }

    private void updateLabel(final StepLocation stepLocation) {
        if (stepLocation.getMetaId() == 0 && stepLocation.getPartIndex() == 0 && stepLocation.getRecordIndex() == 0) {
            getView().getLabel().getElement().setInnerHTML("[??:??:??]");
        } else {
            getView().getLabel().getElement().setInnerHTML("[" +
                                                           stepLocation.getMetaId() + ":" +
                                                           (stepLocation.getPartIndex() + 1) + ":" +
                                                           (stepLocation.getRecordIndex() + 1) +
                                                           "]");
        }
    }

    public HandlerRegistration addStepControlHandler(final StepControlHandler handler) {
        return addHandlerToSource(StepControlEvent.getType(), handler);
    }

    public interface StepLocationLinkView extends View {

        Label getLabel();
    }
}
