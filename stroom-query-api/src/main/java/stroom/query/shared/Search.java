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

import java.util.Map;

public class Search implements SharedObject {
    private static final long serialVersionUID = 9055582579670841979L;

    private DocRef dataSourceRef;
    private ExpressionOperator expression;
    private Map<String, ComponentSettings> componentSettingsMap;
    private boolean incremental;

    public Search() {
        // Default constructor necessary for GWT serialisation.
    }

    public Search(final DocRef dataSourceRef, final ExpressionOperator expression,
                  final Map<String, ComponentSettings> componentSettingsMap, final boolean incremental) {
        this.dataSourceRef = dataSourceRef;
        this.expression = expression;
        this.componentSettingsMap = componentSettingsMap;
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

    public boolean isIncremental() {
        return incremental;
    }
}
