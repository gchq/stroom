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

import stroom.ai.client.AiConfigTableAnalysisPresenter.AiConfigTableAnalysisView;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewImpl;

public class AiConfigTableAnalysisViewImpl
        extends ViewImpl
        implements AiConfigTableAnalysisView {

    private final Widget widget;

    @UiField
    ValueSpinner maxTotalRows;
    @UiField
    ValueSpinner maxRowsPerBatch;
    @UiField
    ValueSpinner maxParallelBatches;
    @UiField
    TextArea tableQuerySystemPrompt;
    @UiField
    TextArea tableQueryUserPrompt;
    @UiField
    TextArea summaryMergePrompt;
    @UiField
    TextArea multiSummaryMergePrompt;
    @UiField
    ValueSpinner attachmentDownloadTimeoutMs;

    @Inject
    public AiConfigTableAnalysisViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        maxTotalRows.setMin(1);
        maxTotalRows.setMax(1_000_000);
        maxRowsPerBatch.setMin(1);
        maxRowsPerBatch.setMax(1_000_000);
        maxParallelBatches.setMin(1);
        maxParallelBatches.setMax(100);

        attachmentDownloadTimeoutMs.setMin(1000);
        attachmentDownloadTimeoutMs.setMax(600_000);
        attachmentDownloadTimeoutMs.setDelta(1000);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void focus() {
        maxTotalRows.focus();
    }

    // ---------------------------------------------------------------------

    @Override
    public void setMaxTotalRows(final int maxTotalRows) {
        this.maxTotalRows.setValue(maxTotalRows);
    }

    @Override
    public int getMaxTotalRows() {
        return maxTotalRows.getIntValue();
    }

    @Override
    public void setMaxRowsPerBatch(final int maxRowsPerBatch) {
        this.maxRowsPerBatch.setValue(maxRowsPerBatch);
    }

    @Override
    public int getMaxRowsPerBatch() {
        return maxRowsPerBatch.getIntValue();
    }

    @Override
    public void setMaxParallelBatches(final int maxParallelBatches) {
        this.maxParallelBatches.setValue(maxParallelBatches);
    }

    @Override
    public int getMaxParallelBatches() {
        return maxParallelBatches.getIntValue();
    }

    @Override
    public void setTableQuerySystemPrompt(final String prompt) {
        tableQuerySystemPrompt.setText(prompt);
    }

    @Override
    public String getTableQuerySystemPrompt() {
        return tableQuerySystemPrompt.getText();
    }

    @Override
    public void setTableQueryUserPrompt(final String prompt) {
        tableQueryUserPrompt.setText(prompt);
    }

    @Override
    public String getTableQueryUserPrompt() {
        return tableQueryUserPrompt.getText();
    }

    @Override
    public void setSummaryMergePrompt(final String prompt) {
        summaryMergePrompt.setText(prompt);
    }

    @Override
    public String getSummaryMergePrompt() {
        return summaryMergePrompt.getText();
    }

    @Override
    public void setMultiSummaryMergePrompt(final String prompt) {
        multiSummaryMergePrompt.setText(prompt);
    }

    @Override
    public String getMultiSummaryMergePrompt() {
        return multiSummaryMergePrompt.getText();
    }

    @Override
    public void setAttachmentDownloadTimeoutMs(final long timeoutMs) {
        attachmentDownloadTimeoutMs.setValue((int) timeoutMs);
    }

    @Override
    public long getAttachmentDownloadTimeoutMs() {
        return attachmentDownloadTimeoutMs.getIntValue();
    }

    // ---------------------------------------------------------------------

    public interface Binder extends UiBinder<Widget, AiConfigTableAnalysisViewImpl> {

    }
}
