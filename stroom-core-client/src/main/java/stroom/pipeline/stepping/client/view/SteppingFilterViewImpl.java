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

import stroom.item.client.SelectionBox;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter.SteppingFilterView;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.function.Consumer;

public class SteppingFilterViewImpl extends ViewImpl implements SteppingFilterView {

    private final Widget widget;
    @UiField
    Label nameLabel;
    @UiField
    SimplePanel elementChooser;
    @UiField
    SelectionBox<Severity> skipToErrors;
    @UiField
    SelectionBox<OutputState> skipToOutput;
    @UiField
    SimplePanel xPathList;

    private Consumer<Severity> skipToErrorsValueConsumer;

    private Consumer<OutputState> skipToOutputValueConsumer;

    @Inject
    public SteppingFilterViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        skipToErrors.setNonSelectString("");
        skipToErrors.addItem(Severity.INFO);
        skipToErrors.addItem(Severity.WARNING);
        skipToErrors.addItem(Severity.ERROR);
        skipToErrors.addItem(Severity.FATAL_ERROR);

        skipToOutput.setNonSelectString("");
        skipToOutput.addItem(OutputState.NOT_EMPTY);
        skipToOutput.addItem(OutputState.EMPTY);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setElementChooser(final Widget view) {
        elementChooser.setWidget(view);
    }

    @Override
    public void setName(final String name) {
        nameLabel.setText(name);
    }

    @Override
    public Severity getSkipToErrors() {
        return skipToErrors.getValue();
    }

    @Override
    public void setSkipToErrors(final Severity value) {
        skipToErrors.setValue(value);
    }

    @Override
    public void setSkipToErrorsChangeHandler(final Consumer<Severity> skipToErrorsValueConsumer) {
        this.skipToErrorsValueConsumer = skipToErrorsValueConsumer;
    }

    @Override
    public OutputState getSkipToOutput() {
        return skipToOutput.getValue();
    }

    @Override
    public void setSkipToOutput(final OutputState value) {
        skipToOutput.setValue(value);
    }

    @Override
    public void setSkipToOutputChangeHandler(final Consumer<OutputState> skipToOutputValueConsumer) {
        this.skipToOutputValueConsumer = skipToOutputValueConsumer;
    }

    @Override
    public void setXPathList(final View view) {
        xPathList.setWidget(view.asWidget());
    }

    @UiHandler("skipToErrors")
    public void onSkipToErrorsValueChange(final ValueChangeEvent<Severity> e) {
        if (skipToErrorsValueConsumer != null) {
            skipToErrorsValueConsumer.accept(getSkipToErrors());
        }
    }

    @UiHandler("skipToOutput")
    public void onSkipToOutputValueChange(final ValueChangeEvent<OutputState> e) {
        if (skipToOutputValueConsumer != null) {
            skipToOutputValueConsumer.accept(getSkipToOutput());
        }
    }

    public interface Binder extends UiBinder<Widget, SteppingFilterViewImpl> {

    }
}
