/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.query.language.functions.ref.StoredValues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.exception.JsonQueryException;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Jq.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "The string value of the matched JSON element(s).",
        signatures = @FunctionSignature(
                description = "Extracts values from a JSON string using a JQ expression.",
                args = {
                        @FunctionArg(
                                name = "json",
                                description = "The JSON string to evaluate.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "jq",
                                description = "The JQ expression to use for extraction.",
                                argType = ValString.class)}))
class Jq extends AbstractManyChildFunction {

    static final String NAME = "jq";
    private Generator gen;
    private boolean simple;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Scope ROOT_SCOPE = Scope.newEmptyScope();

    static {
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, ROOT_SCOPE);
    }

    public Jq(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        // See if this is a static computation.
        simple = true;
        for (final Param param : params) {
            if (!(param instanceof Val)) {
                simple = false;
                break;
            }
        }

        if (simple) {
            // Static computation.
            final String json = params[0].toString();
            final String jqPattern = params[1].toString();

            if (jqPattern.isEmpty()) {
                gen = new StaticValueFunction(ValErr.create(
                        "An empty JQ expression has been defined for second argument of '" + name + "' function"))
                        .createGenerator();
            } else if (json == null) {
                gen = new StaticValueFunction(ValNull.INSTANCE).createGenerator();
            } else {
                try {
                    final JsonQuery query = JsonQuery.compile(jqPattern, Versions.JQ_1_6);
                    final JsonNode inNode = OBJECT_MAPPER.readTree(json);
                    final List<JsonNode> out = new ArrayList<>();
                    query.apply(ROOT_SCOPE, inNode, out::add);

                    final Val result = nodesToVal(out);
                    gen = new StaticValueFunction(result).createGenerator();
                } catch (final Exception e) {
                    gen = new StaticValueFunction(ValErr.create(e.getMessage())).createGenerator();
                }
            }

        } else {
            if (params[1] instanceof Val) {
                // Test JQ is valid.
                final String jqPattern = params[1].toString();
                if (jqPattern.isEmpty()) {
                    throw new ParseException(
                            "An empty JQ expression has been defined for second argument of '" + name + "' function", 0);
                }
                try {
                    JsonQuery.compile(jqPattern, Versions.JQ_1_6);
                } catch (final JsonQueryException e) {
                    throw new ParseException("Error in JQ expression: " + e.getMessage(), 0);
                }
            }
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }
        return super.createGenerator();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    @Override
    public boolean hasAggregate() {
        if (simple) {
            return false;
        }
        return super.hasAggregate();
    }

    private static Val nodesToVal(final List<JsonNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return ValNull.INSTANCE;
        }
        if (nodes.size() == 1) {
            final JsonNode node = nodes.get(0);
            if (node.isNull()) {
                return ValNull.INSTANCE;
            }
            if (node.isValueNode()) {
                return ValString.create(node.asText());
            } else {
                return ValString.create(node.toString());
            }
        }
        // Multiple nodes - return as an array string
        return ValString.create(nodes.toString());
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private JsonQuery staticQuery;

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
            // If the JQ pattern is a constant, we can pre-compile it for this generator.
            if (childGenerators[1] instanceof StaticValueGen staticGen) {
                final Val val = staticGen.eval(null, null);
                if (val.type().isValue()) {
                    final String jqPattern = val.toString();
                    if (!jqPattern.isEmpty()) {
                        try {
                            staticQuery = JsonQuery.compile(jqPattern, Versions.JQ_1_6);
                        } catch (JsonQueryException e) {
                            // Ignore and re-compile during eval if needed.
                        }
                    }
                }
            }
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val valJson = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!valJson.type().isValue()) {
                return valJson;
            }
            final Val valJq = childGenerators[1].eval(storedValues, childDataSupplier);
            if (!valJq.type().isValue()) {
                return ValErr.wrap(valJq);
            }

            try {
                final String json = valJson.toString();
                final String jqPattern = valJq.toString();
                
                if (json == null) {
                    return ValNull.INSTANCE;
                }

                JsonQuery query = staticQuery;
                if (query == null) {
                    query = JsonQuery.compile(jqPattern, Versions.JQ_1_6);
                }

                final JsonNode inNode = OBJECT_MAPPER.readTree(json);
                final List<JsonNode> out = new ArrayList<>();
                query.apply(ROOT_SCOPE, inNode, out::add);

                return nodesToVal(out);

            } catch (final Exception e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
