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

import stroom.docref.DocRef;
import stroom.docs.shared.Description;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@Description(
        "Defines a report that can be run at scheduled intervals and sent to individuals via email.\n" +
        "The criteria is defined using a StroomQL query.")
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ReportDoc extends AbstractAnalyticRuleDoc {

    public static final String TYPE = "Report";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.REPORT_DOCUMENT_TYPE;

    @JsonProperty
    private final ReportSettings reportSettings;

    @SuppressWarnings("checkstyle:linelength")
    @JsonCreator
    public ReportDoc(@JsonProperty("uuid") final String uuid,
                     @JsonProperty("name") final String name,
                     @JsonProperty("version") final String version,
                     @JsonProperty("createTimeMs") final Long createTimeMs,
                     @JsonProperty("updateTimeMs") final Long updateTimeMs,
                     @JsonProperty("createUser") final String createUser,
                     @JsonProperty("updateUser") final String updateUser,
                     @JsonProperty("description") final String description,
                     @JsonProperty("includeRuleDocumentation") final Boolean includeRuleDocumentation,
                     @JsonProperty("languageVersion") final QueryLanguageVersion languageVersion,
                     @JsonProperty("parameters") final List<Param> parameters,
                     @JsonProperty("timeRange") final TimeRange timeRange,
                     @JsonProperty("query") final String query,
                     @JsonProperty("analyticProcessType") final AnalyticProcessType analyticProcessType,
                     @JsonProperty("analyticProcessConfig") final AnalyticProcessConfig analyticProcessConfig,
                     @Deprecated @JsonProperty("analyticNotificationConfig") final NotificationConfig analyticNotificationConfig,
                     @JsonProperty("notifications") final List<NotificationConfig> notifications,
                     @JsonProperty("errorFeed") final DocRef errorFeed,
                     @JsonProperty("rememberNotifications") final boolean rememberNotifications,
                     @JsonProperty("suppressDuplicateNotifications") final boolean suppressDuplicateNotifications,
                     @JsonProperty("duplicateNotificationConfig") final DuplicateNotificationConfig duplicateNotificationConfig,
                     @JsonProperty("reportSettings") final ReportSettings reportSettings) {
        super(TYPE, uuid,
                name,
                version,
                createTimeMs,
                updateTimeMs,
                createUser,
                updateUser,
                description,
                includeRuleDocumentation,
                languageVersion,
                parameters,
                timeRange,
                query,
                analyticProcessType,
                analyticProcessConfig,
                analyticNotificationConfig,
                notifications,
                errorFeed,
                rememberNotifications,
                suppressDuplicateNotifications,
                duplicateNotificationConfig);
        this.reportSettings = reportSettings;
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    public ReportSettings getReportSettings() {
        return reportSettings;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ReportDoc reportDoc = (ReportDoc) o;
        return Objects.equals(reportSettings, reportDoc.reportSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reportSettings);
    }

    @Override
    public String toString() {
        return "ReportDoc{" +
               "reportSettings=" + reportSettings +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractAnalyticRuleDocBuilder<ReportDoc, Builder> {

        private ReportSettings reportSettings;

        public Builder() {
        }

        public Builder(final ReportDoc doc) {
            super(doc);
            this.reportSettings = doc.reportSettings;
        }

        public Builder reportSettings(final ReportSettings reportSettings) {
            this.reportSettings = reportSettings;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ReportDoc build() {
            return new ReportDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    includeRuleDocumentation,
                    languageVersion,
                    parameters,
                    timeRange,
                    query,
                    analyticProcessType,
                    analyticProcessConfig,
                    null,
                    notifications,
                    errorFeed,
                    false,
                    false,
                    duplicateNotificationConfig,
                    reportSettings);
        }
    }
}
