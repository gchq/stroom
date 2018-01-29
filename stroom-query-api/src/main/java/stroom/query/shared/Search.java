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
    private Map<String, String> paramMap = Collections.emptyMap();
    private String dateTimeLocale = "UTC";
    private boolean incremental = true;
    private boolean storeHistory = false;
    private String searchPurpose;

    public Search() {
        // Default constructor necessary for GWT serialisation.
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression) {
        this(dataSourceRef, expression, null, Collections.emptyMap(), "UTC", true, false, null);
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap) {
        this(dataSourceRef, expression, componentSettingsMap, Collections.emptyMap(), "UTC", true, false, null);
    }

    public Search(final DocRef dataSourceRef,
                  final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap,
                  final Map<String, String> paramMap,
                  final String dateTimeLocale,
                  final boolean incremental,
                  final boolean storeHistory,
                  final String searchPurpose) {
        this.dataSourceRef = dataSourceRef;
        this.expression = expression;
        this.componentSettingsMap = componentSettingsMap;
        this.paramMap = paramMap;
        this.dateTimeLocale = dateTimeLocale;
        this.incremental = incremental;
        this.storeHistory = storeHistory;
        this.searchPurpose = searchPurpose;
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

    public String getSearchPurpose() {
        return searchPurpose;
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
        public Builder dateTimeLocale(final String dateTimeLocale) {
            this.instance.dateTimeLocale = dateTimeLocale;
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

        public Builder searchPurpose(final String searchPurpose) {
            this.instance.searchPurpose = searchPurpose;
            return this;
        }

        public Search build() {
            return this.instance;
        }
    }
}
