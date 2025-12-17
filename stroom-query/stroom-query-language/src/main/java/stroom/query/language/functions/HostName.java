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

import java.net.InetAddress;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = HostName.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        signatures = @FunctionSignature(
                description = "Returns the host name for the given host string.",
                args = {
                        @FunctionArg(
                                name = "host",
                                argType = ValString.class,
                                description = "The host address or name.")
                }
        )
)
class HostName extends AbstractFunction {

    static final String NAME = "hostName";

    HostName() {
        super(NAME, 1, 1);
    }

    public HostName(final String functionName) {
        super(NAME, 1, 1);
    }

    public HostName(final Object expressionContext, final String functionName) {
        super(NAME, 1, 1);
    }

    @Override
    public Generator createGenerator() {
        final Param param = params[0];
        if (param instanceof Function) {
            final Generator gen = ((Function) param).createGenerator();
            return new Generator() {
                @Override
                public void set(final Val[] values, final StoredValues storedValues) {
                    gen.set(values, storedValues);
                }

                @Override
                public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
                    final Val val = gen.eval(storedValues, childDataSupplier);
                    return resolveHostName(val);
                }

                @Override
                public void merge(final StoredValues existingValues, final StoredValues newValues) {
                    gen.merge(existingValues, newValues);
                }
            };
        } else if (param instanceof final Val val) {
            return new Generator() {
                @Override
                public void set(final Val[] values, final StoredValues storedValues) {
                    // No-op
                }

                @Override
                public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
                    return resolveHostName(val);
                }

                @Override
                public void merge(final StoredValues existingValues, final StoredValues newValues) {
                    // No-op
                }
            };
        } else {
            throw new RuntimeException("Invalid parameter type for HostName: " + param.getClass());
        }
    }

    private Val resolveHostName(final Val val) {
        if (val == null || val.type().isError()) {
            return val;
        }
        final String host = val.toString();
        try {
            return ValString.create(InetAddress.getByName(host).getHostName());
        } catch (final Exception e) {
            return ValErr.create(e.getMessage());
        }
    }

    @Override
    public boolean hasAggregate() {
        return false;
    }
}
