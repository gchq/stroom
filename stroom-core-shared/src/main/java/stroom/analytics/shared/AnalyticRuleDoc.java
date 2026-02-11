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
        "Defines an analytic rule which can be run to alert on events meeting a criteria.\n" +
        "The criteria is defined using a StroomQL query.\n" +
        "The analytic can be processed in different ways:\n\n" +
        "* Streaming\n" +
        "* Table Builder\n" +
        "* Scheduled Query")
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticRuleDoc extends AbstractAnalyticRuleDoc {

    public static final String TYPE = "AnalyticRule";
    public static final DocumentType DOCUMENT_TYPE = DocumentTypeRegistry.ANALYTIC_RULE_DOCUMENT_TYPE;

    private static final boolean INCLUDE_RULE_DOCUMENTATION_DEFAULT_VALUE = true;

    @JsonProperty
    private final boolean includeRuleDocumentation;

    @SuppressWarnings("checkstyle:linelength")
    @JsonCreator
    public AnalyticRuleDoc(@JsonProperty("uuid") final String uuid,
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
                           @JsonProperty("duplicateNotificationConfig") final DuplicateNotificationConfig duplicateNotificationConfig) {
        super(TYPE, uuid,
                name,
                version,
                createTimeMs,
                updateTimeMs,
                createUser,
                updateUser,
                description,
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
        this.includeRuleDocumentation = includeRuleDocumentation == null
                ? INCLUDE_RULE_DOCUMENTATION_DEFAULT_VALUE
                : includeRuleDocumentation;
    }

    /**
     * The includeRuleDocumentation field determines whether a rule's documentation
     * will be included in any detections that it produces.
     *
     * @return boolean value of includeRuleDocumentation
     */
    public boolean isIncludeRuleDocumentation() {
        return includeRuleDocumentation;
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AnalyticRuleDoc that = (AnalyticRuleDoc) o;
        return includeRuleDocumentation == that.includeRuleDocumentation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), includeRuleDocumentation);
    }

    @Override
    public String toString() {
        return "AnalyticRuleDoc{" +
               "includeRuleDocumentation=" + includeRuleDocumentation +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractAnalyticRuleDocBuilder<AnalyticRuleDoc, Builder> {

        boolean includeRuleDocumentation = INCLUDE_RULE_DOCUMENTATION_DEFAULT_VALUE;

        private Builder() {
        }

        private Builder(final AnalyticRuleDoc doc) {
            super(doc);
            this.includeRuleDocumentation = doc.includeRuleDocumentation;
        }

        public Builder includeRuleDocumentation(final boolean includeRuleDocumentation) {
            this.includeRuleDocumentation = includeRuleDocumentation;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AnalyticRuleDoc build() {
            return new AnalyticRuleDoc(
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
                    duplicateNotificationConfig);
        }
    }
}
