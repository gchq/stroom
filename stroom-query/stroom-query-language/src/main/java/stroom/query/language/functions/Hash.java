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
import stroom.util.shared.NullSafe;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.HexFormat;
import java.util.Objects;
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
class Hash extends AbstractManyChildFunction {

    static final String NAME = "hash";

    static final String DEFAULT_ALGORITHM = "SHA-256";
    static final MessageDigest DEFAULT_ALGORITHM_DIGEST;
    static final Function DEFAULT_ALGORITHM_FUNC = StaticValueFunction.of(DEFAULT_ALGORITHM);

    private static final HexFormat HEX_FORMAT = HexFormat.of().withLowerCase();

    static {
        try {
            DEFAULT_ALGORITHM_DIGEST = MessageDigest.getInstance(DEFAULT_ALGORITHM);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Generator generator;
    private MessageDigest messageDigest;

    private boolean simple;

    public Hash(final String name) {
        super(name, 1, 3);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);
        final int paramCount = params.length;

        if (paramCount < 1) {
            throw new ParseException("Expected to get at least one argument to '" + name + "' function", 0);
        }

        if (paramCount > 3) {
            throw new ParseException("Expected to get at most three arguments to '" + name + "' function", 0);
        }

        // See if this is a purely static computation.
        simple = ParamParseUtil.supportsStaticComputation(params);

        if (simple) {
            // All static values, however unlikely
            final String valueToHash = ParamParseUtil.parseStringParam(params, 0, name);
            String algorithm = paramCount >= 2
                    ? ParamParseUtil.parseStringParam(params, 1, name)
                    : null;
            algorithm = Objects.requireNonNullElse(algorithm, DEFAULT_ALGORITHM);
            final String salt = paramCount == 3
                    ? ParamParseUtil.parseStringParam(params, 2, name)
                    : null;

            try {
                final MessageDigest messageDigest = Objects.equals(algorithm, DEFAULT_ALGORITHM)
                        ? DEFAULT_ALGORITHM_DIGEST
                        : MessageDigest.getInstance(algorithm);
                generator = StaticValueFunction.of(hash(valueToHash, messageDigest, salt))
                        .createGenerator();
            } catch (final NoSuchAlgorithmException e) {
                throw new ParseException("Second argument of '" + name + "' function '" + algorithm
                                         + "' is not a valid hash algorithm name.", 0);
            }
        } else {
            // If we have a static algorithm param, then get the digest to hold on the
            // generator, so we don't need to get it for each row.
            if (paramCount >= 2) {
                final Param algoParam = params[1];
                if (algoParam instanceof final Val val) {
                    final String algo = val.toString();
                    messageDigest = verifyAndGetAlgorithm(algo);
                }
            }
        }
    }

    private static String hash(final String value,
                               final String algorithm,
                               final String salt) throws ParseException {
        final MessageDigest digest = algorithm != null
                ? verifyAndGetAlgorithm(algorithm)
                : DEFAULT_ALGORITHM_DIGEST;
        return hash(value, digest, salt);
    }

    private static String hash(final String value,
                               final MessageDigest messageDigest,
                               final String salt) {
        final MessageDigest digest = Objects.requireNonNullElse(messageDigest, DEFAULT_ALGORITHM_DIGEST);
        digest.reset();
        if (salt != null) {
            digest.update(salt.getBytes());
        }
        final byte[] arr = digest.digest(value.getBytes());
        return HEX_FORMAT.formatHex(arr);
    }

    private static MessageDigest verifyAndGetAlgorithm(final String algorithm) throws ParseException {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (final NoSuchAlgorithmException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }

    @Override
    public Generator createGenerator() {
        if (generator != null) {
            return generator;
        }
        return super.createGenerator();
    }

    @Override
    public boolean hasAggregate() {
        if (simple) {
            return false;
        }
        return super.hasAggregate();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new ManyChildGenerator(childGenerators, messageDigest);
    }

    @Override
    public boolean requiresChildData() {
        return super.requiresChildData();
    }


    // --------------------------------------------------------------------------------


    /**
     * For cases where the algorithm and salt are both static
     */
    private static final class SingleChildGenerator extends AbstractSingleChildGenerator {

        private final MessageDigest messageDigest;
        private final String salt;

        SingleChildGenerator(final Generator childGenerator,
                             final MessageDigest messageDigest,
                             final String salt) {
            super(childGenerator);
            this.messageDigest = messageDigest;
            this.salt = salt;
        }

        @Override
        public void set(final Val[] values, final StoredValues storedValues) {
            childGenerator.set(values, storedValues);
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val val = childGenerator.eval(storedValues, childDataSupplier);
            if (!val.type().isValue()) {
                return ValErr.wrap(val, "Unable to convert argument to string");
            }

            try {
                return ValString.create(hash(val.toString(), messageDigest, salt));
            } catch (final RuntimeException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static final class ManyChildGenerator extends AbstractManyChildGenerator {

        private MessageDigest messageDigest = DEFAULT_ALGORITHM_DIGEST;

        ManyChildGenerator(final Generator[] childGenerators,
                           final MessageDigest messageDigest) {
            super(childGenerators);
            this.messageDigest = messageDigest;
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {

            final Val valToHash = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!valToHash.type().isValue()) {
                return ValErr.wrap(valToHash, "First argument of '" + Hash.NAME + "' is not a value.");
            }

            final Val algo;
            if (childGenerators.length > 1) {
                algo = childGenerators[1].eval(storedValues, childDataSupplier);
                if (Val.isNull(algo)) {
                    messageDigest = DEFAULT_ALGORITHM_DIGEST;
                } else if (!algo.type().isValue()) {
                    return ValErr.wrap(algo, "Second argument of '" + Hash.NAME + "' is not a value.");
                }
            } else {
                algo = ValNull.INSTANCE;
            }

            final Val salt;
            if (childGenerators.length > 2) {
                salt = childGenerators[2].eval(storedValues, childDataSupplier);
                if (!salt.type().isValue()) {
                    return ValErr.wrap(salt, "Third argument of '" + Hash.NAME + "' is not a value.");
                }
            } else {
                salt = ValNull.INSTANCE;
            }

            try {
                if (messageDigest != null) {
                    return ValString.create(hash(
                            NullSafe.get(valToHash, Val::toString),
                            messageDigest,
                            NullSafe.get(salt, Val::toString)));
                } else {
                    return ValString.create(hash(
                            NullSafe.get(valToHash, Val::toString),
                            NullSafe.getOrElse(algo, Val::toString, DEFAULT_ALGORITHM),
                            NullSafe.get(salt, Val::toString)));
                }

            } catch (final RuntimeException | ParseException e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
