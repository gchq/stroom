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

package stroom.analytics.shared;

import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.docref.DocRef;
import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ReportSettings {

    public static final DownloadSearchResultFileType DEFAULT_FILE_TYPE = DownloadSearchResultFileType.EXCEL;

    /**
     * What the model is asked of the report's data when the report does not say.
     */
    public static final String DEFAULT_AI_SUMMARY_PROMPT =
            "Summarise this report for someone who has to act on it. Say what the data shows, call out "
            + "anything unusual or worth attention, and keep it to a few short paragraphs.";

    @JsonProperty
    private final DownloadSearchResultFileType fileType;
    @JsonProperty
    private final boolean sendEmptyReports;
    @JsonProperty
    private final boolean aiSummaryEnabled;
    @JsonProperty
    private final DocRef aiSummaryModel;
    @JsonProperty
    private final String aiSummaryPrompt;

    @JsonCreator
    public ReportSettings(@JsonProperty("fileType") final DownloadSearchResultFileType fileType,
                          @JsonProperty("sendEmptyReports") final Boolean sendEmptyReports,
                          @JsonProperty("aiSummaryEnabled") final Boolean aiSummaryEnabled,
                          @JsonProperty("aiSummaryModel") final DocRef aiSummaryModel,
                          @JsonProperty("aiSummaryPrompt") final String aiSummaryPrompt) {
        this.fileType = Objects.requireNonNullElse(fileType, DEFAULT_FILE_TYPE);
        this.sendEmptyReports = sendEmptyReports == null || sendEmptyReports;
        this.aiSummaryEnabled = aiSummaryEnabled != null && aiSummaryEnabled;
        this.aiSummaryModel = aiSummaryModel;
        this.aiSummaryPrompt = aiSummaryPrompt;
    }

    public DownloadSearchResultFileType getFileType() {
        return fileType;
    }

    public boolean isSendEmptyReports() {
        return sendEmptyReports;
    }

    /**
     * @return Whether to ask a model to summarise the report's data and deliver that alongside it.
     */
    public boolean isAiSummaryEnabled() {
        return aiSummaryEnabled;
    }

    /**
     * @return The model to ask, or null to use the one configured for Ask Stroom AI.
     */
    public DocRef getAiSummaryModel() {
        return aiSummaryModel;
    }

    /**
     * @return What to ask of the data, or null for {@link #DEFAULT_AI_SUMMARY_PROMPT}.
     */
    public String getAiSummaryPrompt() {
        return aiSummaryPrompt;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReportSettings that = (ReportSettings) o;
        return sendEmptyReports == that.sendEmptyReports
               && aiSummaryEnabled == that.aiSummaryEnabled
               && fileType == that.fileType
               && Objects.equals(aiSummaryModel, that.aiSummaryModel)
               && Objects.equals(aiSummaryPrompt, that.aiSummaryPrompt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileType, sendEmptyReports, aiSummaryEnabled, aiSummaryModel, aiSummaryPrompt);
    }

    @Override
    public String toString() {
        return "ReportSettings{" +
               "fileType=" + fileType +
               ", sendEmptyReports=" + sendEmptyReports +
               ", aiSummaryEnabled=" + aiSummaryEnabled +
               ", aiSummaryModel=" + aiSummaryModel +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractBuilder<ReportSettings, Builder> {

        private DownloadSearchResultFileType fileType;
        private boolean sendEmptyReports = true;
        private boolean aiSummaryEnabled;
        private DocRef aiSummaryModel;
        private String aiSummaryPrompt;

        public Builder() {
        }

        public Builder(final ReportSettings settings) {
            this.fileType = settings.fileType;
            this.sendEmptyReports = settings.sendEmptyReports;
            this.aiSummaryEnabled = settings.aiSummaryEnabled;
            this.aiSummaryModel = settings.aiSummaryModel;
            this.aiSummaryPrompt = settings.aiSummaryPrompt;
        }

        public Builder fileType(final DownloadSearchResultFileType fileType) {
            this.fileType = Objects.requireNonNull(fileType);
            return self();
        }

        public Builder sendEmptyReports(final boolean sendEmptyReports) {
            this.sendEmptyReports = sendEmptyReports;
            return self();
        }

        public Builder aiSummaryEnabled(final boolean aiSummaryEnabled) {
            this.aiSummaryEnabled = aiSummaryEnabled;
            return self();
        }

        public Builder aiSummaryModel(final DocRef aiSummaryModel) {
            this.aiSummaryModel = aiSummaryModel;
            return self();
        }

        public Builder aiSummaryPrompt(final String aiSummaryPrompt) {
            this.aiSummaryPrompt = aiSummaryPrompt;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ReportSettings build() {
            return new ReportSettings(
                    fileType, sendEmptyReports, aiSummaryEnabled, aiSummaryModel, aiSummaryPrompt);
        }
    }
}
