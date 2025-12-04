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

package stroom.query.language.functions;

import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = QueryParams.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        signatures = @FunctionSignature(
                description = "Returns all the query parameters for the current query, e.g. 'user=jbloggs site=HQ'.",
                returnDescription = "All query parameters as a space delimited string.",
                args = {}))
class QueryParams extends AbstractFunction {

    static final String NAME = "params";

    private Generator gen = Null.GEN;

    public QueryParams(final String name) {
        super(name, 0, 0);
    }

    @Override
    public void setStaticMappedValues(final Map<String, String> staticMappedValues) {
        if (staticMappedValues != null) {
            final String str = staticMappedValues.entrySet()
                    .stream()
                    .filter(entry -> !ParamKeys.INTERNAL_PARAM_KEYS.contains(entry.getKey()))
                    .map(entry ->
                            entry.getKey() + "=\"" + entry.getValue() + "\"")
                    .collect(Collectors.joining(" "));
            gen = new StaticValueGen(ValString.create(str));
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
