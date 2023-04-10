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

import com.google.common.io.BaseEncoding;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.function.Supplier;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = Hash.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "The hash string.",
        signatures = {
                @FunctionSignature(
                        description = "Generate a " + Hash.DEFAULT_ALGORITHM + " hash of the input string.",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        description = "Value to hash.",
                                        argType = ValString.class)
                        }),
                @FunctionSignature(
                        description = "Generate a hash of the input string using the supplied hash algorithm.",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        description = "Value to hash.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "algorithm",
                                        description = "The name of the hash algorithm, e.g. 'SHA-256', 'SHA-512', " +
                                                "'MD5' etc.",
                                        argType = ValString.class)
                        }),
                @FunctionSignature(
                        description = "Generate a hash of the input string using the supplied hash algorithm and salt.",
                        args = {
                                @FunctionArg(
                                        name = "value",
                                        description = "Value to hash.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "algorithm",
                                        description = "The name of the hash algorithm, e.g. 'SHA-256', 'SHA-512', " +
                                                "'MD5' etc.",
                                        argType = ValString.class),
                                @FunctionArg(
                                        name = "salt",
                                        description = "The salt value to create the hash with.",
                                        argType = ValString.class)})})
class Hash extends AbstractFunction {

    static final String NAME = "hash";

    static final String DEFAULT_ALGORITHM = "SHA-256";

    private String algorithm = DEFAULT_ALGORITHM;
    private String salt;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public Hash(final String name) {
        super(name, 1, 3);
    }

    private static String hash(final String value,
                               final String algorithm,
                               final String salt) throws NoSuchAlgorithmException {
        // Create MessageDigest object.
        final MessageDigest digest = MessageDigest.getInstance(algorithm);
        if (salt != null) {
            digest.update(salt.getBytes());
        }

        final byte[] arr = digest.digest(value.getBytes());

        // TODO: 30/01/2023 In Java17+ use HexFormat class
        return BaseEncoding.base16()
                .lowerCase()
                .encode(arr);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length >= 2) {
            algorithm = ParamParseUtil.parseStringParam(params, 1, name);
            if (algorithm == null) {
                algorithm = DEFAULT_ALGORITHM;
            }
        }
        if (params.length >= 3) {
            salt = ParamParseUtil.parseStringParam(params, 2, name);
        }

        try {
            // Test that the algorithm is a valid one.
            MessageDigest.getInstance(algorithm);

            final Param param = params[0];
            if (param instanceof Function) {
                function = (Function) param;
                hasAggregate = function.hasAggregate();

            } else {
                final String string = param.toString();
                if (string == null) {
                    throw new ParseException("Unable to convert first argument of '" + name + "' function to string",
                            0);
                }
                gen = new StaticValueFunction(ValString.create(hash(string, algorithm, salt))).createGenerator();
            }
        } catch (final NoSuchAlgorithmException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }

        final Generator childGenerator = function.createGenerator();
        return new Gen(childGenerator, algorithm, salt);
    }

    @Override
    public boolean hasAggregate() {
        return hasAggregate;
    }

    private static final class Gen extends AbstractSingleChildGenerator {

        private final String algorithm;
        private final String salt;

        Gen(final Generator childGenerator, final String algorithm, final String salt) {
            super(childGenerator);
            this.algorithm = algorithm;
            this.salt = salt;
        }

        @Override
        public void set(final Val[] values) {
            childGenerator.set(values);
        }

        @Override
        public Val eval(final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerator.eval(childDataSupplier);
            if (!val.type().isValue()) {
                return ValErr.wrap(val, "Unable to convert argument to string");
            }

            try {
                return ValString.create(hash(val.toString(), algorithm, salt));
            } catch (final NoSuchAlgorithmException | RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
