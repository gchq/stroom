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

package stroom.query.shared;

import stroom.entity.shared.DocRef;
import stroom.util.shared.SharedObject;

import java.util.Collections;
import java.util.Map;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "search", propOrder = {"dataSourceRef", "expression", "componentSettingsMap", "paramMap", "dateTimeLocale", "incremental"})
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
    private String dateTimeLocale;

    @XmlElement
    private boolean incremental;

    public Search() {
        // Default constructor necessary for GWT serialisation.
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression) {
        this(dataSourceRef, expression, null, Collections.emptyMap(), "UTC", true);
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap) {
        this(dataSourceRef, expression, componentSettingsMap, Collections.emptyMap(), "UTC", true);
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap, final Map<String, String> paramMap,
                  final String dateTimeLocale, final boolean incremental) {
        this.dataSourceRef = dataSourceRef;
        this.expression = expression;
        this.componentSettingsMap = componentSettingsMap;
        this.paramMap = paramMap;
        this.dateTimeLocale = dateTimeLocale;
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

    public String getDateTimeLocale() {
        return dateTimeLocale;
    }

    public boolean isIncremental() {
        return incremental;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Search search = (Search) o;

        return new EqualsBuilder()
                .append(incremental, search.incremental)
                .append(dataSourceRef, search.dataSourceRef)
                .append(expression, search.expression)
                .append(componentSettingsMap, search.componentSettingsMap)
                .append(paramMap, search.paramMap)
                .isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(dataSourceRef);
        hashCodeBuilder.append(expression);
        hashCodeBuilder.append(componentSettingsMap);
        hashCodeBuilder.append(paramMap);
        hashCodeBuilder.append(incremental);
        return hashCodeBuilder.toHashCode();
    }

    @Override
    public String toString() {
        return "Search{" +
                "dataSourceRef=" + dataSourceRef +
                ", expression=" + expression +
                ", componentSettingsMap=" + componentSettingsMap +
                ", paramMap=" + paramMap +
                ", incremental=" + incremental +
                '}';
    }
}