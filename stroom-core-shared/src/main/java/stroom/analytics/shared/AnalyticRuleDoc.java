/*
 * Copyright 2016 Crown Copyright
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
import stroom.docstore.shared.Doc;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.TimeRange;
import stroom.svg.shared.SvgImage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
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
public class AnalyticRuleDoc extends Doc {

    public static final String DOCUMENT_TYPE = "AnalyticRule";
    public static final SvgImage ICON = SvgImage.DOCUMENT_ANALYTIC_RULE;

    @JsonProperty
    private final String description;
    @JsonProperty
    private final QueryLanguageVersion languageVersion;
    @JsonProperty
    private final List<Param> parameters;
    @JsonProperty
    private final TimeRange timeRange;
    @JsonProperty
    private String query;
    @JsonProperty
    private final AnalyticProcessType analyticProcessType;
    @JsonProperty
    private final AnalyticProcessConfig analyticProcessConfig;
    @JsonProperty
    @Deprecated
    private final NotificationConfig analyticNotificationConfig;
    @JsonProperty
    private final List<NotificationConfig> notifications;
    @JsonProperty
    private final DocRef errorFeed;
    @JsonProperty
    private final boolean rememberNotifications;
    @JsonProperty
    private final boolean suppressDuplicateNotifications;

    public AnalyticRuleDoc() {
        description = null;
        languageVersion = null;
        parameters = null;
        timeRange = null;
        query = null;
        analyticProcessType = null;
        analyticProcessConfig = null;
        analyticNotificationConfig = null;
        notifications = new ArrayList<>();
        errorFeed = null;
        rememberNotifications = false;
        suppressDuplicateNotifications = false;
    }

    @SuppressWarnings("checkstyle:linelength")
    @JsonCreator
    public AnalyticRuleDoc(@JsonProperty("type") final String type,
                           @JsonProperty("uuid") final String uuid,
                           @JsonProperty("name") final String name,
                           @JsonProperty("version") final String version,
                           @JsonProperty("createTimeMs") final Long createTimeMs,
                           @JsonProperty("updateTimeMs") final Long updateTimeMs,
                           @JsonProperty("createUser") final String createUser,
                           @JsonProperty("updateUser") final String updateUser,
                           @JsonProperty("description") final String description,
                           @JsonProperty("languageVersion") final QueryLanguageVersion languageVersion,
                           @JsonProperty("parameters") final List<Param> parameters,
                           @JsonProperty("timeRange") final TimeRange timeRange,
                           @JsonProperty("query") final String query,
                           @JsonProperty("analyticProcessType") AnalyticProcessType analyticProcessType,
                           @JsonProperty("analyticProcessConfig") final AnalyticProcessConfig analyticProcessConfig,
                           @Deprecated @JsonProperty("analyticNotificationConfig") final NotificationConfig analyticNotificationConfig,
                           @JsonProperty("notifications") final List<NotificationConfig> notifications,
                           @JsonProperty("errorFeed") final DocRef errorFeed,
                           @JsonProperty("rememberNotifications") final boolean rememberNotifications,
                           @JsonProperty("suppressDuplicateNotifications") final boolean suppressDuplicateNotifications) {
        super(type, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        this.description = description;
        this.languageVersion = languageVersion;
        this.parameters = parameters;
        this.timeRange = timeRange;
        this.query = query;
        this.analyticProcessType = analyticProcessType;
        this.analyticProcessConfig = analyticProcessConfig;
        this.analyticNotificationConfig = null;
        this.notifications = new ArrayList<>();
        if (notifications != null) {
            this.notifications.addAll(notifications);
        }
        if (analyticNotificationConfig != null) {
            this.notifications.add(analyticNotificationConfig);
        }
        this.errorFeed = errorFeed;
        this.rememberNotifications = rememberNotifications;
        this.suppressDuplicateNotifications = suppressDuplicateNotifications;
    }

    /**
     * @return A new {@link DocRef} for this document's type with the supplied uuid.
     */
    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(DOCUMENT_TYPE)
                .uuid(uuid)
                .build();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(DOCUMENT_TYPE);
    }

    public String getDescription() {
        return description;
    }

    public QueryLanguageVersion getLanguageVersion() {
        return languageVersion;
    }

    public List<Param> getParameters() {
        return parameters;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(final String query) {
        this.query = query;
    }

    public AnalyticProcessType getAnalyticProcessType() {
        return analyticProcessType;
    }

    public AnalyticProcessConfig getAnalyticProcessConfig() {
        return analyticProcessConfig;
    }

//    @Deprecated
//    public AnalyticNotificationConfig getAnalyticNotificationConfig() {
//        return analyticNotificationConfig;
//    }


    public List<NotificationConfig> getNotifications() {
        return notifications;
    }

    public DocRef getErrorFeed() {
        return errorFeed;
    }

    public boolean isRememberNotifications() {
        return rememberNotifications;
    }

    public boolean isSuppressDuplicateNotifications() {
        return suppressDuplicateNotifications;
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
        final AnalyticRuleDoc that = (AnalyticRuleDoc) o;
        return rememberNotifications == that.rememberNotifications &&
                suppressDuplicateNotifications == that.suppressDuplicateNotifications &&
                Objects.equals(description, that.description) &&
                languageVersion == that.languageVersion &&
                Objects.equals(parameters, that.parameters) &&
                Objects.equals(timeRange, that.timeRange) &&
                Objects.equals(query, that.query) &&
                analyticProcessType == that.analyticProcessType &&
                Objects.equals(analyticProcessConfig, that.analyticProcessConfig) &&
                Objects.equals(analyticNotificationConfig, that.analyticNotificationConfig) &&
                Objects.equals(notifications, that.notifications) &&
                Objects.equals(errorFeed, that.errorFeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
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
                suppressDuplicateNotifications);
    }

    @Override
    public String toString() {
        return "AnalyticRuleDoc{" +
                "description='" + description + '\'' +
                ", languageVersion=" + languageVersion +
                ", parameters=" + parameters +
                ", timeRange=" + timeRange +
                ", query='" + query + '\'' +
                ", analyticProcessType=" + analyticProcessType +
                ", analyticProcessConfig=" + analyticProcessConfig +
                ", analyticNotificationConfig=" + analyticNotificationConfig +
                ", notifications=" + notifications +
                ", errorFeed=" + errorFeed +
                ", rememberNotifications=" + rememberNotifications +
                ", suppressDuplicateNotifications=" + suppressDuplicateNotifications +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder extends AbstractBuilder<AnalyticRuleDoc, Builder> {

        private String description;
        private QueryLanguageVersion languageVersion;
        private List<Param> parameters;
        private TimeRange timeRange;
        private String query;
        private AnalyticProcessType analyticProcessType;
        private AnalyticProcessConfig analyticProcessConfig;
        private List<NotificationConfig> notifications = new ArrayList<>();
        private DocRef errorFeed;
        private boolean rememberNotifications;
        private boolean suppressDuplicateNotifications;

        public Builder() {
        }

        public Builder(final AnalyticRuleDoc doc) {
            super(doc);
            this.description = doc.description;
            this.languageVersion = doc.languageVersion;
            this.parameters = doc.parameters;
            this.timeRange = doc.timeRange;
            this.query = doc.query;
            this.analyticProcessType = doc.analyticProcessType;
            this.analyticProcessConfig = doc.analyticProcessConfig;
            this.notifications = new ArrayList<>(doc.notifications);
            this.errorFeed = doc.errorFeed;
            this.rememberNotifications = doc.rememberNotifications;
            this.suppressDuplicateNotifications = doc.suppressDuplicateNotifications;
        }

        public Builder description(final String description) {
            this.description = description;
            return self();
        }

        public Builder languageVersion(final QueryLanguageVersion languageVersion) {
            this.languageVersion = languageVersion;
            return self();
        }

        public Builder parameters(final List<Param> parameters) {
            this.parameters = parameters;
            return self();
        }

        public Builder timeRange(final TimeRange timeRange) {
            this.timeRange = timeRange;
            return self();
        }

        public Builder query(final String query) {
            this.query = query;
            return self();
        }

        public Builder analyticProcessType(final AnalyticProcessType analyticProcessType) {
            this.analyticProcessType = analyticProcessType;
            return self();
        }

        public Builder analyticProcessConfig(final AnalyticProcessConfig analyticProcessConfig) {
            this.analyticProcessConfig = analyticProcessConfig;
            return self();
        }

        public Builder notifications(final List<NotificationConfig> notifications) {
            this.notifications = new ArrayList<>(notifications);
            return self();
        }

        public Builder errorFeed(final DocRef errorFeed) {
            this.errorFeed = errorFeed;
            return this;
        }

        public Builder rememberNotifications(final boolean rememberNotifications) {
            this.rememberNotifications = rememberNotifications;
            return this;
        }

        public Builder suppressDuplicateNotifications(final boolean suppressDuplicateNotifications) {
            this.suppressDuplicateNotifications = suppressDuplicateNotifications;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AnalyticRuleDoc build() {
            return new AnalyticRuleDoc(
                    type,
                    uuid,
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
                    null,
                    notifications,
                    errorFeed,
                    rememberNotifications,
                    suppressDuplicateNotifications);
        }
    }
}
