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

import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;

class Hash extends AbstractFunction implements Serializable {
    static final String NAME = "hash";
    private static final long serialVersionUID = -305845496003936297L;
    private static final String DEFAULT_ALGORITHM = "SHA-256";

    private String algorithm = DEFAULT_ALGORITHM;
    private String salt;

    private Generator gen;
    private Function function;
    private boolean hasAggregate;

    public Hash(final String name) {
        super(name, 1, 3);
    }

    private static String hash(final String value, final String algorithm, final String salt) throws NoSuchAlgorithmException {
        // Create MessageDigest object.
        final MessageDigest digest = MessageDigest.getInstance(algorithm);
        if (salt != null) {
            digest.update(salt.getBytes());
        }

        final byte[] arr = digest.digest(value.getBytes());
        // Converts message digest value in base 16 (hex)
        return new BigInteger(1, arr).toString(16);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        if (params.length >= 2) {
            algorithm = ParamParseUtil.parseStringParam(params, 1, name);
        }
        if (params.length >= 3) {
            salt =ParamParseUtil.parseStringParam(params,2, name);
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
                    throw new ParseException("Unable to convert first argument of '" + name + "' function to string", 0);
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

    private static class Gen extends AbstractSingleChildGenerator {
        private static final long serialVersionUID = 8153777070911899616L;

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
        public Val eval() {
            final Val val = childGenerator.eval();
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
