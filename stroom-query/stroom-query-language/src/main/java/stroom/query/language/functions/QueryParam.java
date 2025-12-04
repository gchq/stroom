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

import java.text.ParseException;
import java.util.Map;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = QueryParam.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        signatures = @FunctionSignature(
                description = "Fetches the value of named query parameter or " + Null.NAME + "() if the key " +
                        "cannot be found.",
                returnDescription = "The value associated with the supplied parameter key.",
                args = {
                        @FunctionArg(
                                name = "paramKey",
                                description = "The parameter key name to fetch the value for.",
                                argType = Val.class)
                }))
class QueryParam extends AbstractFunction {

    static final String NAME = "param";

    private Generator gen = Null.GEN;

    private String key;
    private String value;

    public QueryParam(final String name) {
        super(name, 1, 1);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params[0] instanceof Val) {
            key = params[0].toString();
            if (key == null || key.length() == 0) {
                throw new ParseException(
                        "Argument of '" + name + "' is expected to be a parameter key name", 0);
            }
        } else {
            throw new ParseException(
                    "Argument of '" + name + "' is expected to be a parameter key name", 0);
        }
    }

    @Override
    public void setStaticMappedValues(final Map<String, String> staticMappedValues) {
        if (key == null) {
            throw new RuntimeException("Key must be set before calling setStaticMappedValues");
        }

        final String v = staticMappedValues.get(key);
        if (v != null) {
            gen = new StaticValueGen(ValString.create(v));
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
