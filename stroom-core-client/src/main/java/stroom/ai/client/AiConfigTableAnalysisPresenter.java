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
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class AiConfigTableAnalysisPresenter
        extends MyPresenterWidget<AiConfigTableAnalysisView> {

    @Inject
    public AiConfigTableAnalysisPresenter(final EventBus eventBus,
                                          final AiConfigTableAnalysisView view) {
        super(eventBus, view);
    }

    // ---------------------------------------------------------------------

    public void read(final TableAnalysisConfig config,
                     final AskStroomAIConfig askConfig) {
        getView().setMaxTotalRows(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getMaxTotalRows,
                TableAnalysisConfig.DEFAULT_MAX_TOTAL_ROWS));
        getView().setMaxRowsPerBatch(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getMaxRowsPerBatch,
                TableAnalysisConfig.DEFAULT_MAX_ROWS_PER_BATCH));
        getView().setMaxParallelBatches(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getMaxParallelBatches,
                TableAnalysisConfig.DEFAULT_MAX_PARALLEL_BATCHES));
        getView().setTableQuerySystemPrompt(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getTableQuerySystemPrompt,
                TableAnalysisConfig.DEFAULT_TABLE_QUERY_SYSTEM_PROMPT));
        getView().setTableQueryUserPrompt(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getTableQueryUserPrompt,
                TableAnalysisConfig.DEFAULT_TABLE_QUERY_USER_PROMPT));
        getView().setMultiSummaryMergePrompt(NullSafe.getOrElse(
                config,
                TableAnalysisConfig::getMultiSummaryMergePrompt,
                TableAnalysisConfig.DEFAULT_MULTI_SUMMARY_MERGE_PROMPT));
        getView().setAttachmentDownloadTimeoutMs(NullSafe.getOrElse(
                askConfig,
                AskStroomAIConfig::getAttachmentDownloadTimeoutMs,
                AskStroomAIConfig.DEFAULT_ATTACHMENT_DOWNLOAD_TIMEOUT_MS));
    }

    public TableAnalysisConfig write() {
        return new TableAnalysisConfig(
                getView().getMaxTotalRows(),
                getView().getMaxRowsPerBatch(),
                getView().getMaxParallelBatches(),
                getView().getTableQuerySystemPrompt(),
                getView().getTableQueryUserPrompt(),
                getView().getMultiSummaryMergePrompt());
    }

    public long getAttachmentDownloadTimeoutMs() {
        return getView().getAttachmentDownloadTimeoutMs();
    }

    // ---------------------------------------------------------------------


    public interface AiConfigTableAnalysisView extends View, Focus {

        void setMaxTotalRows(int maxTotalRows);

        int getMaxTotalRows();

        void setMaxRowsPerBatch(int maxRowsPerBatch);

        int getMaxRowsPerBatch();

        void setMaxParallelBatches(int maxParallelBatches);

        int getMaxParallelBatches();

        void setTableQuerySystemPrompt(String prompt);

        String getTableQuerySystemPrompt();

        void setTableQueryUserPrompt(String prompt);

        String getTableQueryUserPrompt();

        void setMultiSummaryMergePrompt(String prompt);

        String getMultiSummaryMergePrompt();

        void setAttachmentDownloadTimeoutMs(long timeoutMs);

        long getAttachmentDownloadTimeoutMs();
    }
}
