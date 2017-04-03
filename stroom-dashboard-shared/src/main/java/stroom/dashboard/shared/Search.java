/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.shared;

import stroom.query.api.DocRef;
import stroom.query.api.ExpressionOperator;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "search", propOrder = {"dataSourceRef", "expression", "componentSettingsMap", "paramMap", "incremental"})
public class Search implements Serializable {
    private static final long serialVersionUID = 9055582579670841979L;

    @XmlElement
    private DocRef dataSourceRef;

    @XmlElement
    private ExpressionOperator expression;

    @XmlElement
    private Map<String, ComponentSettings> componentSettingsMap;

    @XmlElement
    private Map<String, String> paramMap;

    @XmlElement
    private Boolean incremental;

    public Search() {
        // Default constructor necessary for GWT serialisation.
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression) {
        this(dataSourceRef, expression, null, Collections.emptyMap(), true);
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap) {
        this(dataSourceRef, expression, componentSettingsMap, Collections.emptyMap(), true);
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap, final Map<String, String> paramMap, final Boolean incremental) {
        this.dataSourceRef = dataSourceRef;
        this.expression = expression;
        this.componentSettingsMap = componentSettingsMap;
        this.paramMap = paramMap;
        this.incremental = incremental;
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

    public Boolean getIncremental() {
        return incremental;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final Search search = (Search) o;

        if (dataSourceRef != null ? !dataSourceRef.equals(search.dataSourceRef) : search.dataSourceRef != null)
            return false;
        if (expression != null ? !expression.equals(search.expression) : search.expression != null) return false;
        if (componentSettingsMap != null ? !componentSettingsMap.equals(search.componentSettingsMap) : search.componentSettingsMap != null)
            return false;
        if (paramMap != null ? !paramMap.equals(search.paramMap) : search.paramMap != null) return false;
        return incremental != null ? incremental.equals(search.incremental) : search.incremental == null;
    }

    @Override
    public int hashCode() {
        int result = dataSourceRef != null ? dataSourceRef.hashCode() : 0;
        result = 31 * result + (expression != null ? expression.hashCode() : 0);
        result = 31 * result + (componentSettingsMap != null ? componentSettingsMap.hashCode() : 0);
        result = 31 * result + (paramMap != null ? paramMap.hashCode() : 0);
        result = 31 * result + (incremental != null ? incremental.hashCode() : 0);
        return result;
    }
}