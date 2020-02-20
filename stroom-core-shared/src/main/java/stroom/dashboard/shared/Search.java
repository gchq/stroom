/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 */

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "search", propOrder = {"dataSourceRef", "expression", "componentSettingsMap", "paramMap", "incremental", "storeHistory", "queryInfo"})
@JsonInclude(Include.NON_DEFAULT)
public class Search implements Serializable {
    private static final long serialVersionUID = 9055582579670841979L;

    @XmlElement
    @JsonProperty
    private DocRef dataSourceRef;
    @XmlElement
    @JsonProperty
    private ExpressionOperator expression;
    @XmlElement
    @JsonProperty
    private Map<String, ComponentSettings> componentSettingsMap;
    @XmlElement
    @JsonProperty
    private Map<String, String> paramMap = Collections.emptyMap();
    @XmlElement
    @JsonProperty
    private boolean incremental;
    @XmlElement
    @JsonProperty
    private boolean storeHistory;
    @XmlElement
    @JsonProperty
    private String queryInfo;

    public Search() {
    }

    @JsonCreator
    public Search(@JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                  @JsonProperty("expression") final ExpressionOperator expression,
                  @JsonProperty("componentSettingsMap") final Map<String, ComponentSettings> componentSettingsMap,
                  @JsonProperty("paramMap") final Map<String, String> paramMap,
                  @JsonProperty("incremental") final boolean incremental,
                  @JsonProperty("storeHistory") final boolean storeHistory,
                  @JsonProperty("queryInfo") final String queryInfo) {
        this.dataSourceRef = dataSourceRef;
        this.expression = expression;
        this.componentSettingsMap = componentSettingsMap;
        this.paramMap = paramMap;
        this.incremental = incremental;
        this.storeHistory = storeHistory;
        this.queryInfo = queryInfo;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public Map<String, ComponentSettings> getComponentSettingsMap() {
        return componentSettingsMap;
    }

    public Map<String, String> getParamMap() {
        return paramMap;
    }

    public void setParamMap(final Map<String, String> paramMap) {
        this.paramMap = paramMap;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public boolean isStoreHistory() {
        return storeHistory;
    }

    public String getQueryInfo() {
        return queryInfo;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Search search = (Search) o;

        if (incremental != search.incremental) return false;
        if (storeHistory != search.storeHistory) return false;
        if (dataSourceRef != null ? !dataSourceRef.equals(search.dataSourceRef) : search.dataSourceRef != null)
            return false;
        if (expression != null ? !expression.equals(search.expression) : search.expression != null) return false;
        if (componentSettingsMap != null ? !componentSettingsMap.equals(search.componentSettingsMap) : search.componentSettingsMap != null)
            return false;
        return paramMap != null ? paramMap.equals(search.paramMap) : search.paramMap == null;
    }

    @Override
    public int hashCode() {
        int result = dataSourceRef != null ? dataSourceRef.hashCode() : 0;
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        result = 31 * result + (componentSettingsMap != null ? componentSettingsMap.hashCode() : 0);
        result = 31 * result + (paramMap != null ? paramMap.hashCode() : 0);
        result = 31 * result + (incremental ? 1 : 0);
        result = 31 * result + (storeHistory ? 1 : 0);
        return result;
    }

    public static class Builder {
        private final Search instance;

        public Builder() {
            this.instance = new Search();
        }

        public Builder dataSourceRef(final DocRef dataSourceRef) {
            this.instance.dataSourceRef = dataSourceRef;
            return this;
        }

        public Builder expression(final ExpressionOperator expression) {
            this.instance.expression = expression;
            return this;
        }

        public Builder componentSettingsMap(final Map<String, ComponentSettings> componentSettingsMap) {
            this.instance.componentSettingsMap = componentSettingsMap;
            return this;
        }

        public Builder paramMap(final Map<String, String> paramMap) {
            this.instance.paramMap = paramMap;
            return this;
        }

        public Builder incremental(final boolean incremental) {
            this.instance.incremental = incremental;
            return this;
        }

        public Builder storeHistory(final boolean storeHistory) {
            this.instance.storeHistory = storeHistory;
            return this;
        }

        public Builder queryInfo(final String queryInfo) {
            this.instance.queryInfo = queryInfo;
            return this;
        }

        public Search build() {
            return this.instance;
        }
    }
}
