/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AskStroomAiConfigPresenter.AskStroomAiConfigView;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.button.client.Button;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class AskStroomAiConfigViewImpl
        extends ViewWithUiHandlers<AskStroomAiConfigUiHandlers>
        implements AskStroomAiConfigView {

    private final Widget widget;

    @UiField
    ValueSpinner maximumBatchSize;
    @UiField
    ValueSpinner maximumTableInputRows;
    @UiField
    ValueSpinner memoryTokenLimit;
    @UiField
    DurationPicker memoryTimeToLive;
    @UiField
    Button setDefault;

    @Inject
    public AskStroomAiConfigViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setDefault.setVisible(false);

        maximumBatchSize.setMin(1);
        maximumBatchSize.setMax(1000000);
        maximumTableInputRows.setMin(1);
        maximumTableInputRows.setMax(1000000);
        memoryTokenLimit.setMin(1);
        memoryTokenLimit.setMax(1000000);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        maximumBatchSize.focus();
    }

    @Override
    public void allowSetDefault(final boolean allow) {
        setDefault.setVisible(allow);
    }

    @Override
    public void setMaximumBatchSize(final int maximumBatchSize) {
        this.maximumBatchSize.setValue(maximumBatchSize);
    }

    @Override
    public int getMaximumBatchSize() {
        return maximumBatchSize.getIntValue();
    }

    @Override
    public void setMaximumTableInputRows(final int maximumTableInputRows) {
        this.maximumTableInputRows.setValue(maximumTableInputRows);
    }

    @Override
    public int getMaximumTableInputRows() {
        return maximumTableInputRows.getIntValue();
    }

    @Override
    public void setMemoryTokenLimit(final int memoryTokenLimit) {
        this.memoryTokenLimit.setValue(memoryTokenLimit);
    }

    @Override
    public int getMemoryTokenLimit() {
        return memoryTokenLimit.getIntValue();
    }

    @Override
    public SimpleDuration getMemoryTimeToLive() {
        return memoryTimeToLive.getValue();
    }

    @Override
    public void setMemoryTimeToLive(final SimpleDuration memoryTimeToLive) {
        this.memoryTimeToLive.setValue(memoryTimeToLive);
    }

    @UiHandler("setDefault")
    public void onSetDefaultClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onSetDefault(setDefault);
        }
    }

    public interface Binder extends UiBinder<Widget, AskStroomAiConfigViewImpl> {

    }
}
