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

package stroom.analytics.shared;

import stroom.dashboard.shared.DownloadSearchResultFileType;
import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

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

    @JsonProperty
    private final DownloadSearchResultFileType fileType;
    @JsonProperty
    private final boolean sendEmptyReports;

    @JsonCreator
    public ReportSettings(@JsonProperty("fileType") final DownloadSearchResultFileType fileType,
                          @JsonProperty("sendEmptyReports") final boolean sendEmptyReports) {
        this.fileType = NullSafe.requireNonNullElse(fileType, DEFAULT_FILE_TYPE);
        this.sendEmptyReports = sendEmptyReports;
    }

    public DownloadSearchResultFileType getFileType() {
        return fileType;
    }

    public boolean isSendEmptyReports() {
        return sendEmptyReports;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ReportSettings that = (ReportSettings) o;
        return sendEmptyReports == that.sendEmptyReports && fileType == that.fileType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileType, sendEmptyReports);
    }

    @Override
    public String toString() {
        return "ReportSettings{" +
               "fileType=" + fileType +
               ", sendEmptyReports=" + sendEmptyReports +
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
        private boolean sendEmptyReports;

        public Builder() {
        }

        public Builder(final ReportSettings settings) {
            this.fileType = settings.fileType;
            this.sendEmptyReports = settings.sendEmptyReports;
        }

        public Builder fileType(final DownloadSearchResultFileType fileType) {
            this.fileType = Objects.requireNonNull(fileType);
            return self();
        }

        public Builder sendEmptyReports(final boolean sendEmptyReports) {
            this.sendEmptyReports = sendEmptyReports;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ReportSettings build() {
            return new ReportSettings(fileType, sendEmptyReports);
        }
    }
}
