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

package stroom.pipeline.stepping.client.view;

import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter.StepControlView;
import stroom.pipeline.stepping.client.presenter.StepControlUIHandlers;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class StepControlViewImpl extends ViewWithUiHandlers<StepControlUIHandlers> implements StepControlView {

    private final Widget widget;
    @UiField
    InlineSvgButton filterButton;
    @UiField
    InlineSvgButton firstButton;
    @UiField
    InlineSvgButton backwardButton;
    @UiField
    InlineSvgButton forwardButton;
    @UiField
    InlineSvgButton lastButton;
    @UiField
    InlineSvgButton refreshButton;

    @Inject
    public StepControlViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        filterButton.setSvg(SvgImage.FILTER);
        filterButton.setTitle("Manage Step Filters");

        firstButton.setSvg(SvgImage.FAST_BACKWARD);
        firstButton.setTitle("Step To First");
        firstButton.setEnabled(false);

        backwardButton.setSvg(SvgImage.STEP_BACKWARD);
        backwardButton.setTitle("Step Backward");
        backwardButton.setEnabled(false);

        forwardButton.setSvg(SvgImage.STEP_FORWARD);
        forwardButton.setTitle("Step Forward");
        forwardButton.setEnabled(false);

        lastButton.setSvg(SvgImage.FAST_FORWARD);
        lastButton.setTitle("Step To Last");
        lastButton.setEnabled(false);

        refreshButton.setSvg(SvgImage.REFRESH);
        refreshButton.setTitle("Refresh Current Step");
        refreshButton.setEnabled(false);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setStepFirstEnabled(final boolean enabled) {
        firstButton.setEnabled(enabled);
    }

    @Override
    public void setStepBackwardEnabled(final boolean enabled) {
        backwardButton.setEnabled(enabled);
    }

    @Override
    public void setStepForwardEnabled(final boolean enabled) {
        forwardButton.setEnabled(enabled);
    }

    @Override
    public void setStepLastEnabled(final boolean enabled) {
        lastButton.setEnabled(enabled);
    }

    @Override
    public void setStepRefreshEnabled(final boolean enabled) {
        refreshButton.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled(final StepType stepType) {
        //noinspection EnhancedSwitchMigration // cos GWT
        switch (stepType) {
            case FIRST:
                return firstButton.isEnabled();
            case BACKWARD:
                return backwardButton.isEnabled();
            case FORWARD:
                return forwardButton.isEnabled();
            case LAST:
                return lastButton.isEnabled();
            case REFRESH:
                return refreshButton.isEnabled();
            default:
                return false;
        }
    }

    @UiHandler("filterButton")
    public void onFilterButtonClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().filter();
        }
    }

    @UiHandler("firstButton")
    public void onFirstButtonClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().stepFirst();
        }
    }

    @UiHandler("backwardButton")
    public void onBackwardButtonClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().stepBackward();
        }
    }

    @UiHandler("forwardButton")
    public void onForwardButtonClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().stepForward();
        }
    }

    @UiHandler("lastButton")
    public void onLastButtonClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().stepLast();
        }
    }

    @UiHandler("refreshButton")
    public void onRefreshButtonClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().stepRefresh();
        }
    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, StepControlViewImpl> {

    }
}
