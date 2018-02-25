/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@Scope(StroomScope.PROTOTYPE)
class Hash extends StroomExtensionFunctionCall {
    private static final String DEFAULT_ALGORITHM = "SHA-256";

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) {
        String result = null;

        try {
            final String value = getSafeString(functionName, context, arguments, 0);

            if (value != null && value.length() > 0) {
                String algorithm = null;
                if (arguments.length > 1) {
                    algorithm = getSafeString(functionName, context, arguments, 1);
                }
                if (algorithm == null || algorithm.trim().length() == 0) {
                    algorithm = DEFAULT_ALGORITHM;
                }

                String salt = null;
                if (arguments.length > 2) {
                    salt = getSafeString(functionName, context, arguments, 2);
                }

                result = hash(value, algorithm, salt);
            }
        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }

    String hash(final String value, final String algorithm, final String salt) throws NoSuchAlgorithmException {
        // Create MessageDigest object.
        final MessageDigest digest = MessageDigest.getInstance(algorithm);
        digest.reset();

        if (salt != null) {
            digest.update(salt.getBytes());
        }

        final byte[] arr = digest.digest(value.getBytes());
        // Converts message digest value in base 16 (hex)
        return new BigInteger(1, arr).toString(16);
    }
}