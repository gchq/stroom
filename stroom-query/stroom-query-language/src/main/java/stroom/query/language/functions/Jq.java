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
import net.thisptr.jackson.jq.BuiltinFunctionLoader;
import net.thisptr.jackson.jq.JsonQuery;
import net.thisptr.jackson.jq.Scope;
import net.thisptr.jackson.jq.Versions;
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
                description = "Extracts values from a JSON string using a JQ expression. Where the expression " +
                              "matches more than one element the string values of all the matched elements are " +
                              "concatenated.",
                args = {
                        @FunctionArg(
                                name = "json",
                                description = "The JSON string to evaluate.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "jq",
                                description = "The JQ expression to use for extraction.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "delimiter",
                                description = "The delimiter to insert between the values of each matched element. " +
                                              "Defaults to no delimiter.",
                                argType = ValString.class,
                                isOptional = true)}))
class Jq extends AbstractManyChildFunction {

    static final String NAME = "jq";

    private static final int JSON_ARG = 0;
    private static final int JQ_ARG = 1;
    private static final int DELIMITER_ARG = 2;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Scope ROOT_SCOPE = Scope.newEmptyScope();

    static {
        BuiltinFunctionLoader.getInstance().loadFunctions(Versions.JQ_1_6, ROOT_SCOPE);
    }

    private Generator gen;
    private boolean simple;

    public Jq(final String name) {
        super(name, 2, 3);
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
            // Static computation. Any bad arguments are reported as an error value rather than a parse failure as
            // all of the arguments, including the JSON, are known at this point.
            gen = new StaticValueFunction(staticEval(params)).createGenerator();

        } else {
            // Validate whatever we can up front so the user gets told about a bad expression at parse time. Note
            // that the expression itself is compiled once per search by the generator, not here, as an expression
            // supplied as a query parameter only becomes constant after the parameters have been statically mapped.
            if (params[JQ_ARG] instanceof Val) {
                final String jqPattern = params[JQ_ARG].toString();
                if (jqPattern.isEmpty()) {
                    throw new ParseException(
                            "An empty JQ expression has been defined for second argument of '" + name
                            + "' function", 0);
                }
                try {
                    compile(jqPattern);
                } catch (final JsonQueryException e) {
                    throw new ParseException("Error in JQ expression: " + getMessage(e), 0);
                }
            }
        }
    }

    private Val staticEval(final Param[] params) {
        final Val valJson = (Val) params[JSON_ARG];
        if (!valJson.type().isValue()) {
            return valJson;
        }

        final String jqPattern = params[JQ_ARG].toString();
        if (jqPattern.isEmpty()) {
            return ValErr.create("An empty JQ expression has been defined for second argument of '" + name
                                 + "' function");
        }

        final Compiled compiled;
        try {
            compiled = compile(jqPattern);
        } catch (final JsonQueryException e) {
            return ValErr.create("Error in JQ expression: " + getMessage(e));
        }

        return evaluate(compiled, valJson.toString(), getArg(params, DELIMITER_ARG));
    }

    private static String getArg(final Param[] params, final int index) {
        return params.length > index
                ? params[index].toString()
                : "";
    }

    /**
     * Compiles the expression. This is only ever done once per expression value, not once per row.
     */
    private static Compiled compile(final String jqPattern) throws JsonQueryException {
        return new Compiled(jqPattern, JsonQuery.compile(jqPattern, Versions.JQ_1_6));
    }

    /**
     * Evaluates the expression and concatenates the string values of all the elements it matches.
     */
    private static Val evaluate(final Compiled compiled,
                                final String json,
                                final String delimiter) {
        if (json == null) {
            return ValNull.INSTANCE;
        }

        try {
            final JsonNode inNode = OBJECT_MAPPER.readTree(json);
            final List<JsonNode> out = new ArrayList<>();
            compiled.query().apply(ROOT_SCOPE, inNode, out::add);
            return nodesToVal(out, delimiter);

        } catch (final Exception e) {
            return ValErr.create(getMessage(e));
        }
    }

    private static Val nodesToVal(final List<JsonNode> nodes, final String delimiter) {
        if (nodes == null || nodes.isEmpty()) {
            return ValNull.INSTANCE;
        }
        if (nodes.size() == 1) {
            final JsonNode node = nodes.getFirst();
            if (node.isNull()) {
                return ValNull.INSTANCE;
            }
            return ValString.create(nodeToString(node));
        }

        // Concatenate the values of all the matched elements.
        final StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (final JsonNode node : nodes) {
            if (!first) {
                sb.append(delimiter);
            }
            sb.append(nodeToString(node));
            first = false;
        }
        return ValString.create(sb.toString());
    }

    private static String nodeToString(final JsonNode node) {
        // Value nodes give us the raw value, anything else, i.e. an object or array, gives us its JSON.
        return node.isValueNode()
                ? node.asText()
                : node.toString();
    }

    private static String getMessage(final Exception e) {
        return e.getMessage() != null
                ? e.getMessage()
                : e.toString();
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


    // --------------------------------------------------------------------------------


    private static final class Gen extends AbstractManyChildGenerator {

        // The expression compiled once when the generator is created, i.e. once per column per search. Only
        // available where the expression is constant, which is the normal case. This is final so that it is safely
        // published to the other threads that share this generator, as a thread seeing null would re-compile the
        // expression for every row.
        private final Compiled constant;

        // Resolved once if the delimiter is constant, which it normally is, else null and resolved per row.
        private final String constantDelimiter;

        // Where the expression comes from a field or parameter we remember the last compilation so that we only
        // re-compile when the expression actually changes. Volatile as this generator is shared between threads,
        // but a lost update is harmless as it just results in another compilation.
        private volatile Compiled lastCompiled;

        Gen(final Generator[] childGenerators) {
            super(childGenerators);

            constantDelimiter = constantValue(childGenerators, DELIMITER_ARG);

            // Note that this happens after any query parameters have been statically mapped, so an expression
            // supplied as a parameter will have become a constant by this point.
            Compiled compiled = null;
            final String jqPattern = constantValue(childGenerators, JQ_ARG);
            if (jqPattern != null && !jqPattern.isEmpty()) {
                try {
                    compiled = compile(jqPattern);
                } catch (final JsonQueryException e) {
                    // Ignore, the error is reported as a value during eval.
                }
            }
            constant = compiled;
        }

        /**
         * @return The value of a constant argument, or null if the argument is present but not constant.
         */
        private static String constantValue(final Generator[] childGenerators, final int index) {
            if (childGenerators.length <= index) {
                return "";
            }
            if (childGenerators[index] instanceof final StaticValueGen staticGen) {
                final Val val = staticGen.eval(null, null);
                if (val.type().isValue()) {
                    return val.toString();
                }
            }
            return null;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val valJson = childGenerators[JSON_ARG].eval(storedValues, childDataSupplier);
            if (!valJson.type().isValue()) {
                return valJson;
            }
            final String delimiter = constantDelimiter != null
                    ? constantDelimiter
                    : evalArg(DELIMITER_ARG, storedValues, childDataSupplier);

            if (constant != null) {
                return evaluate(constant, valJson.toString(), delimiter);
            }

            final Val valJq = childGenerators[JQ_ARG].eval(storedValues, childDataSupplier);
            if (!valJq.type().isValue()) {
                return ValErr.wrap(valJq);
            }
            final String jqPattern = valJq.toString();
            if (jqPattern.isEmpty()) {
                return ValErr.create("An empty JQ expression has been defined for second argument of '"
                                     + NAME + "' function");
            }

            // Only re-compile if the expression has changed since the last row.
            Compiled compiled = lastCompiled;
            if (compiled == null || !compiled.matches(jqPattern)) {
                try {
                    compiled = compile(jqPattern);
                } catch (final JsonQueryException e) {
                    return ValErr.create("Error in JQ expression: " + getMessage(e));
                }
                lastCompiled = compiled;
            }

            return evaluate(compiled, valJson.toString(), delimiter);
        }

        private String evalArg(final int index,
                               final StoredValues storedValues,
                               final Supplier<ChildData> childDataSupplier) {
            if (childGenerators.length <= index) {
                return "";
            }
            final Val val = childGenerators[index].eval(storedValues, childDataSupplier);
            return val.type().isValue()
                    ? val.toString()
                    : "";
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * A compiled expression plus the expression it was compiled from so that we know when it can be reused.
     */
    private record Compiled(String jqPattern, JsonQuery query) {

        private boolean matches(final String jqPattern) {
            return this.jqPattern.equals(jqPattern);
        }
    }
}
