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

package stroom.pipeline.stepping.client.view;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter.StepControlView;
import stroom.pipeline.stepping.client.presenter.StepControlUIHandlers;
import stroom.widget.button.client.GlyphButton;
import stroom.widget.button.client.GlyphIcons;

public class StepControlViewImpl extends ViewWithUiHandlers<StepControlUIHandlers> implements StepControlView {
    public interface Binder extends UiBinder<Widget, StepControlViewImpl> {
    }

    private final Widget widget;

    @UiField(provided = true)
    GlyphButton firstButton;
    @UiField(provided = true)
    GlyphButton backwardButton;
    @UiField(provided = true)
    GlyphButton forwardButton;
    @UiField(provided = true)
    GlyphButton lastButton;
    @UiField(provided = true)
    GlyphButton refreshButton;

    @Inject
    public StepControlViewImpl(final Binder binder) {

        firstButton = createButton("fa fa-fast-backward", "Step First");
        backwardButton = createButton("fa fa-step-backward", "Step Backward");
        forwardButton = createButton("fa fa-step-forward", "Step Forward");
        lastButton = createButton("fa fa-fast-forward", "Step Last");
        refreshButton = createButton("fa fa-refresh", "Refresh Current Step");

        widget = binder.createAndBindUi(this);
    }

    private GlyphButton createButton(final String className, final String title) {
        final GlyphButton button = GlyphButton.create(className, GlyphIcons.GREEN, title, false);
        final Style style = button.getElement().getStyle();
        style.setPadding(1, Style.Unit.PX);
        style.setFloat(Style.Float.RIGHT);
        return button;
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setStepFirstEnabled(boolean enabled) {
        firstButton.setEnabled(enabled);
    }

    @Override
    public void setStepBackwardEnabled(boolean enabled) {
        backwardButton.setEnabled(enabled);
    }

    @Override
    public void setStepForwardEnabled(boolean enabled) {
        forwardButton.setEnabled(enabled);
    }

    @Override
    public void setStepLastEnabled(boolean enabled) {
        lastButton.setEnabled(enabled);
    }

    @Override
    public void setStepRefreshEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
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
}
