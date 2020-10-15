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

package stroom.dashboard.expression.v1;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

class QueryParams extends AbstractFunction {
    private static final Set<String> INTERNAL_PARAMS = Collections.singleton(CurrentUser.KEY);

    static final String NAME = "params";

    private static final Generator NULL_GEN = new StaticValueFunction(ValNull.INSTANCE).createGenerator();

    private Generator gen = NULL_GEN;

    public QueryParams(final String name) {
        super(name, 0, 0);
    }

    @Override
    public void setStaticMappedValues(final Map<String, String> staticMappedValues) {
        if (staticMappedValues != null) {
            final StringBuilder sb = new StringBuilder();
            staticMappedValues.forEach((k, v) -> {
                if (!INTERNAL_PARAMS.contains(k)) {
                    sb.append(k);
                    sb.append("=\"");
                    sb.append(v);
                    sb.append("\" ");
                }
            });
            gen = new StaticValueFunction(ValString.create(sb.toString().trim())).createGenerator();
        }
    }

    @Override
    public Generator createGenerator() {
        return gen;
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }
}
