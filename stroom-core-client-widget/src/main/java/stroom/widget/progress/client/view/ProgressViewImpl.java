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

package stroom.widget.progress.client.view;

import stroom.widget.progress.client.presenter.ProgressPresenter.ProgressView;

import com.google.gwt.dom.client.Style;
import com.google.gwt.dom.client.Style.Cursor;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.function.Consumer;

public class ProgressViewImpl extends ViewImpl implements ProgressView {

    private final Widget widget;

    @UiField
    FlowPanel progressBarContainer;
    @UiField
    FlowPanel progressBarOuter;
    @UiField
    FlowPanel progressBarInner;

    private Consumer<Double> clickPercentageConsumer = null;

    @Inject
    public ProgressViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        progressBarOuter.addDomHandler(getClickHandler(), ClickEvent.getType());
    }

    private Consumer<Double> getClickPercentageConsumer() {
        return clickPercentageConsumer;
    }

    private ClickHandler getClickHandler() {
        return event -> {
            if (getClickPercentageConsumer() != null) {
                final double x = event.getX() - 1; // make zero based
                final double width = event.getRelativeElement().getOffsetWidth();
                // Turn x into a percentage, making sure 0 <= percentage <= 100
                final double percentage = Math.max(
                        0,
                        Math.min(
                                100,
                                (x / width * 100)));

//            GWT.log("x " + event.getX() +
//                    " y " + event.getY() +
//                    " len " + event.getRelativeElement().getClientWidth() +
//                    " pct " + percentage);

                getClickPercentageConsumer()
                        .accept(percentage);
            }
        };
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setRangeFromPct(final double rangeFrom) {
        progressBarInner.getElement()
                .getStyle()
                .setLeft(rangeFrom, Unit.PCT);
    }

    @Override
    public void setProgressPct(final double progress) {
        progressBarInner.getElement()
                .getStyle()
                .setWidth(progress, Unit.PCT);
    }

    @Override
    public void setProgressBarColour(final String colour) {
        progressBarInner.getElement()
                .getStyle()
                .setBackgroundColor(colour);
    }

    @Override
    public void setTitle(final String title) {
        progressBarOuter.getElement()
                .setTitle(title);
        progressBarInner.getElement()
                .setTitle(title);
    }

    @Override
    public void setVisible(final boolean isVisible) {
        progressBarContainer.setVisible(isVisible);
    }

    @Override
    public void setClickHandler(final Consumer<Double> percentageConsumer) {
        final Style style = progressBarOuter.getElement().getStyle();
        if (percentageConsumer == null) {
            style.setCursor(Cursor.DEFAULT);
        } else {
            style.setCursor(Cursor.POINTER);
        }
        clickPercentageConsumer = percentageConsumer;
    }

    public interface Binder extends UiBinder<Widget, ProgressViewImpl> {

    }
}
