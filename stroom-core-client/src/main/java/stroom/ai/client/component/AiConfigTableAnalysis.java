/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client.component;

import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextArea;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Table Analysis tab of the Ask Stroom AI settings: how much of a table is sent to the AI
/// model, in how many batches, with which prompts, and how long to wait for the table's data.
public class AiConfigTableAnalysis extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

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
    TextArea multiSummaryMergePrompt;
    @UiField
    ValueSpinner attachmentDownloadTimeoutMs;

    /// Makes an empty tab.
    public AiConfigTableAnalysis() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows the settings, using the defaults for any the settings don't have.
    ///
    /// @param config    The table analysis settings, or null for the defaults.
    /// @param askConfig The settings they are part of, for the attachment download timeout, or
    ///                  null for its default.
    public void read(final TableAnalysisConfig config, final AskStroomAiConfig askConfig) {
        maxTotalRows.setValue(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getMaxTotalRows,
                TableAnalysisConfig.DEFAULT_MAX_TOTAL_ROWS));
        maxRowsPerBatch.setValue(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getMaxRowsPerBatch,
                TableAnalysisConfig.DEFAULT_MAX_ROWS_PER_BATCH));
        maxParallelBatches.setValue(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getMaxParallelBatches,
                TableAnalysisConfig.DEFAULT_MAX_PARALLEL_BATCHES));
        tableQuerySystemPrompt.setText(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getTableQuerySystemPrompt,
                TableAnalysisConfig.DEFAULT_TABLE_QUERY_SYSTEM_PROMPT));
        tableQueryUserPrompt.setText(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getTableQueryUserPrompt,
                TableAnalysisConfig.DEFAULT_TABLE_QUERY_USER_PROMPT));
        multiSummaryMergePrompt.setText(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getMultiSummaryMergePrompt,
                TableAnalysisConfig.DEFAULT_MULTI_SUMMARY_MERGE_PROMPT));
        attachmentDownloadTimeoutMs.setValue(NullSafe.getOrElse(
                askConfig,
                AskStroomAiConfig::getAttachmentDownloadTimeoutMs,
                AskStroomAiConfig.DEFAULT_ATTACHMENT_DOWNLOAD_TIMEOUT_MS));
    }

    /// @return The table analysis settings shown.
    public TableAnalysisConfig write() {
        return new TableAnalysisConfig(
                maxTotalRows.getIntValue(),
                maxRowsPerBatch.getIntValue(),
                maxParallelBatches.getIntValue(),
                tableQuerySystemPrompt.getText(),
                tableQueryUserPrompt.getText(),
                multiSummaryMergePrompt.getText());
    }

    /// @return The time, in milliseconds, to wait for attached table data to download.
    public long getAttachmentDownloadTimeoutMs() {
        return attachmentDownloadTimeoutMs.getIntValue();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AiConfigTableAnalysis> {

    }
}
