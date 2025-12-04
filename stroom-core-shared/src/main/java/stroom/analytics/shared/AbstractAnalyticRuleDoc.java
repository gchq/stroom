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
import stroom.docstore.shared.AbstractDoc;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public abstract class AbstractAnalyticRuleDoc extends AbstractDoc {

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
    @Deprecated
    private final boolean rememberNotifications;
    @JsonProperty
    @Deprecated
    private final boolean suppressDuplicateNotifications;
    @JsonProperty
    private final DuplicateNotificationConfig duplicateNotificationConfig;

    @SuppressWarnings("checkstyle:linelength")
    @JsonCreator
    public AbstractAnalyticRuleDoc(@JsonProperty("type") final String type,
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
                                   @JsonProperty("analyticProcessType") final AnalyticProcessType analyticProcessType,
                                   @JsonProperty("analyticProcessConfig") final AnalyticProcessConfig analyticProcessConfig,
                                   @Deprecated @JsonProperty("analyticNotificationConfig") final NotificationConfig analyticNotificationConfig,
                                   @JsonProperty("notifications") final List<NotificationConfig> notifications,
                                   @JsonProperty("errorFeed") final DocRef errorFeed,
                                   @JsonProperty("rememberNotifications") final boolean rememberNotifications,
                                   @JsonProperty("suppressDuplicateNotifications") final boolean suppressDuplicateNotifications,
                                   @JsonProperty("duplicateNotificationConfig") final DuplicateNotificationConfig duplicateNotificationConfig) {
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

        if (duplicateNotificationConfig == null) {
            this.duplicateNotificationConfig = new DuplicateNotificationConfig(
                    rememberNotifications,
                    suppressDuplicateNotifications,
                    false,
                    Collections.emptyList());
        } else {
            this.duplicateNotificationConfig = duplicateNotificationConfig;
        }
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

    @Deprecated
    public boolean isRememberNotifications() {
        return rememberNotifications;
    }

    @Deprecated
    public boolean isSuppressDuplicateNotifications() {
        return suppressDuplicateNotifications;
    }

    public DuplicateNotificationConfig getDuplicateNotificationConfig() {
        return duplicateNotificationConfig;
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
        final AbstractAnalyticRuleDoc that = (AbstractAnalyticRuleDoc) o;
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

    public abstract static class AbstractAnalyticRuleDocBuilder
            <T extends AbstractAnalyticRuleDoc, B extends AbstractAnalyticRuleDocBuilder<T, ?>>
            extends AbstractBuilder<T, B> {

        String description;
        QueryLanguageVersion languageVersion;
        List<Param> parameters;
        TimeRange timeRange;
        String query;
        AnalyticProcessType analyticProcessType;
        AnalyticProcessConfig analyticProcessConfig;
        List<NotificationConfig> notifications = new ArrayList<>();
        DocRef errorFeed;
        DuplicateNotificationConfig duplicateNotificationConfig;

        public AbstractAnalyticRuleDocBuilder() {
        }

        public AbstractAnalyticRuleDocBuilder(final AbstractAnalyticRuleDoc doc) {
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
            this.duplicateNotificationConfig = doc.duplicateNotificationConfig;
        }

        public B description(final String description) {
            this.description = description;
            return self();
        }

        public B languageVersion(final QueryLanguageVersion languageVersion) {
            this.languageVersion = languageVersion;
            return self();
        }

        public B parameters(final List<Param> parameters) {
            this.parameters = parameters;
            return self();
        }

        public B timeRange(final TimeRange timeRange) {
            this.timeRange = timeRange;
            return self();
        }

        public B query(final String query) {
            this.query = query;
            return self();
        }

        public B analyticProcessType(final AnalyticProcessType analyticProcessType) {
            this.analyticProcessType = analyticProcessType;
            return self();
        }

        public B analyticProcessConfig(final AnalyticProcessConfig analyticProcessConfig) {
            this.analyticProcessConfig = analyticProcessConfig;
            return self();
        }

        public B notifications(final List<NotificationConfig> notifications) {
            this.notifications = new ArrayList<>(notifications);
            return self();
        }

        public B errorFeed(final DocRef errorFeed) {
            this.errorFeed = errorFeed;
            return self();
        }

        public B duplicateNotificationConfig(final DuplicateNotificationConfig duplicateNotificationConfig) {
            this.duplicateNotificationConfig = duplicateNotificationConfig;
            return self();
        }
    }
}
