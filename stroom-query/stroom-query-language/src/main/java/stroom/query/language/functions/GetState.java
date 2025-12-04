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

import stroom.query.language.functions.ref.StoredValues;

import java.text.ParseException;
import java.util.Objects;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = GetState.NAME,
        commonCategory = FunctionCategory.LOOKUP,
        commonReturnType = ValString.class,
        commonReturnDescription = "The state value if found else null.",
        signatures = @FunctionSignature(
                description = "Lookup a value from a state map using a key and optional effective time for " +
                              "temporally sensitive states.",
                args = {
                        @FunctionArg(
                                name = "map",
                                description = "The name of the map that contains the state.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "key",
                                description = "The key to lookup.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "effectiveTime",
                                description = "The effective time for the state lookup.",
                                argType = ValDate.class,
                                isOptional = true)}))
class GetState extends AbstractManyChildFunction {

    static final String NAME = "getState";
    private final StateFetcher stateFetcher;
    private Generator gen;
    private String map;
    private String key;
    private Long effectiveTimeMs;

    public GetState(final ExpressionContext expressionContext, final String name) {
        super(name, 2, 3);
        this.stateFetcher = expressionContext.getStateFetcher();
        Objects.requireNonNull(stateFetcher, "Null lookup provider");
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params[0] instanceof final Val val) {
            map = val.toString();
        }
        if (params[1] instanceof final Val val) {
            key = val.toString();
        }
        if (params.length > 2) {
            if (params[2] instanceof final Val val) {
                effectiveTimeMs = val.toLong();
            }
        } else {
            effectiveTimeMs = System.currentTimeMillis();
        }

        // If we have values for all params then do a lookup now.
        if (map != null && key != null && effectiveTimeMs != null) {
            // Create static value.
            final Val val = stateFetcher.getState(map, key, effectiveTimeMs);
            gen = new StaticValueGen(val);
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
        return new Gen(stateFetcher, map, key, effectiveTimeMs, childGenerators);
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private final StateFetcher stateProvider;
        private final String map;
        private final String key;
        private final Long effectiveTimeMs;

        Gen(final StateFetcher stateProvider,
            final String map,
            final String key,
            final Long effectiveTimeMs,
            final Generator[] childGenerators) {
            super(childGenerators);
            this.stateProvider = stateProvider;
            this.map = map;
            this.key = key;
            this.effectiveTimeMs = effectiveTimeMs;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            try {
                String map = this.map;
                String key = this.key;
                Long effectiveTimeMs = this.effectiveTimeMs;

                if (map == null) {
                    final Val val = childGenerators[0].eval(storedValues, childDataSupplier);
                    if (val.type().isValue()) {
                        map = val.toString();
                    }
                }

                if (map != null && key == null) {
                    final Val val = childGenerators[1].eval(storedValues, childDataSupplier);
                    if (val.type().isValue()) {
                        key = val.toString();
                    }
                }

                if (map != null && key != null && effectiveTimeMs == null) {
                    final Val val = childGenerators[2].eval(storedValues, childDataSupplier);
                    if (val.type().isValue()) {
                        effectiveTimeMs = val.toLong();
                    }
                }

                Val val = ValNull.INSTANCE;
                if (map != null && key != null && effectiveTimeMs != null) {
                    val = stateProvider.getState(map, key, effectiveTimeMs);
                }
                return val;

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
