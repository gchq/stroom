/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 */

package stroom.query.shared;

import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.Param;
import stroom.query.api.v2.TimeRange;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryContext {

    @JsonProperty
    private final List<Param> params;
    @JsonProperty
    private final TimeRange timeRange;
    @JsonProperty
    private final String queryInfo;
    @JsonProperty
    private final DateTimeSettings dateTimeSettings;

    @JsonCreator
    public QueryContext(@JsonProperty("params") final List<Param> params,
                        @JsonProperty("timeRange") final TimeRange timeRange,
                        @JsonProperty("queryInfo") final String queryInfo,
                        @JsonProperty("dateTimeSettings") final DateTimeSettings dateTimeSettings) {
        this.params = params;
        this.timeRange = timeRange;
        this.queryInfo = queryInfo;
        this.dateTimeSettings = dateTimeSettings;
    }

    public List<Param> getParams() {
        return params;
    }

    public TimeRange getTimeRange() {
        return timeRange;
    }

    public String getQueryInfo() {
        return queryInfo;
    }

    public DateTimeSettings getDateTimeSettings() {
        return dateTimeSettings;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryContext that = (QueryContext) o;
        return Objects.equals(params, that.params) &&
                Objects.equals(timeRange, that.timeRange) &&
                Objects.equals(queryInfo, that.queryInfo) &&
                Objects.equals(dateTimeSettings, that.dateTimeSettings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(params, timeRange, queryInfo, dateTimeSettings);
    }

    @Override
    public String toString() {
        return "QueryContext{" +
                "params=" + params +
                ", timeRange=" + timeRange +
                ", queryInfo='" + queryInfo + '\'' +
                ", dateTimeSettings=" + dateTimeSettings +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private List<Param> params;
        private TimeRange timeRange;
        private String queryInfo;
        private DateTimeSettings dateTimeSettings;

        private Builder() {
        }

        private Builder(final QueryContext queryContext) {
            this.params = queryContext.params;
            this.timeRange = queryContext.timeRange;
            this.queryInfo = queryContext.queryInfo;
            this.dateTimeSettings = queryContext.dateTimeSettings;
        }

        public Builder params(final List<Param> params) {
            this.params = params;
            return this;
        }

        public Builder timeRange(final TimeRange timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public Builder queryInfo(final String queryInfo) {
            this.queryInfo = queryInfo;
            return this;
        }

        public Builder dateTimeSettings(final DateTimeSettings dateTimeSettings) {
            this.dateTimeSettings = dateTimeSettings;
            return this;
        }

        public QueryContext build() {
            return new QueryContext(
                    params,
                    timeRange,
                    queryInfo,
                    dateTimeSettings);
        }
    }
}
