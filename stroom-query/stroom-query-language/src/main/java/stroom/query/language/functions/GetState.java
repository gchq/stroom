/*
 * Copyright 2017 Crown Copyright
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
import stroom.query.language.token.Param;

import java.text.ParseException;
import java.time.Instant;
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
    private final StateFetcher stateProvider;
    private Generator gen;
    private String map;
    private String key;
    private Instant effectiveTime;

    public GetState(final ExpressionContext expressionContext, final String name) {
        super(name, 2, 3);
        this.stateProvider = expressionContext.getStateProvider();
        Objects.requireNonNull(stateProvider, "Null lookup provider");
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
                effectiveTime = Instant.ofEpochMilli(val.toLong());
            }
        } else {
            effectiveTime = Instant.now();
        }

        // If we have values for all params then do a lookup now.
        if (map != null && key != null && effectiveTime != null) {
            // Create static value.
            final Val val = stateProvider.getState(map, key, effectiveTime);
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
        return new Gen(stateProvider, map, key, effectiveTime, childGenerators);
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private final StateFetcher stateProvider;
        private final String map;
        private final String key;
        private final Instant effectiveTime;

        Gen(final StateFetcher stateProvider,
            final String map,
            final String key,
            final Instant effectiveTime,
            final Generator[] childGenerators) {
            super(childGenerators);
            this.stateProvider = stateProvider;
            this.map = map;
            this.key = key;
            this.effectiveTime = effectiveTime;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            try {
                String map = this.map;
                String key = this.key;
                Instant effectiveTime = this.effectiveTime;

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

                if (map != null && key != null && effectiveTime == null) {
                    final Val val = childGenerators[2].eval(storedValues, childDataSupplier);
                    if (val.type().isValue()) {
                        effectiveTime = Instant.ofEpochMilli(val.toLong());
                    }
                }

                Val val = ValNull.INSTANCE;
                if (map != null && key != null && effectiveTime != null) {
                    val = stateProvider.getState(map, key, effectiveTime);
                }
                return val;

            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
