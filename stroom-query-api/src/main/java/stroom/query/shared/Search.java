/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.query.shared;

import stroom.entity.shared.DocRef;
import stroom.util.shared.SharedObject;

import java.util.Collections;
import java.util.Map;

public class Search implements SharedObject {
    private static final long serialVersionUID = 9055582579670841979L;

    private DocRef dataSourceRef;
    private ExpressionOperator expression;
    private Map<String, ComponentSettings> componentSettingsMap;
    private Map<String, String> paramMap;
    private String dateTimeLocale;
    private boolean incremental;
    private boolean storeHistory;

    public Search() {
        // Default constructor necessary for GWT serialisation.
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression) {
        this(dataSourceRef, expression, null, Collections.emptyMap(), "UTC", true, false);
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap) {
        this(dataSourceRef, expression, componentSettingsMap, Collections.emptyMap(), "UTC", true, false);
    }

    public Search(final DocRef dataSourceRef,
                  final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap,
                  final Map<String, String> paramMap,
                  final String dateTimeLocale,
                  final boolean incremental,
                  final boolean storeHistory) {
        this.dataSourceRef = dataSourceRef;
        this.expression = expression;
        this.componentSettingsMap = componentSettingsMap;
        this.paramMap = paramMap;
        this.dateTimeLocale = dateTimeLocale;
        this.incremental = incremental;
        this.storeHistory = storeHistory;
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

    public boolean isStoreHistory() {
        return storeHistory;
    }
}
