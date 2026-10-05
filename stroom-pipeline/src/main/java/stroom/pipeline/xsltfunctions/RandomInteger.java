/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.Int64Value;

import java.util.concurrent.ThreadLocalRandom;

@XsltFunctionDef(
        name = RandomInteger.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.VALUE,
        commonDescription = "Generates a random integer between 0 (inclusive) and the upper bound (exclusive).",
        commonReturnType = XsltDataType.INTEGER,
        commonReturnDescription = "The random integer.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "upperBound",
                                        description = "The upper bound of the random integer (exclusive).",
                                        argType = XsltDataType.INTEGER
                                )
                        }
                )
        })
class RandomInteger extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "random-integer";

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        Integer randomInt = null;
        try {
            if (arguments.length != 1) {
                throw new IllegalArgumentException("Expected 1 argument, but got " + arguments.length);
            }
            final int upperBound = Integer.parseInt(getSafeString(functionName, context, arguments, 0));
            if (upperBound <= 0) {
                throw new IllegalArgumentException("Upper bound must be greater than 0");
            }
            randomInt = ThreadLocalRandom.current()
                    .nextInt(upperBound);
        } catch (final RuntimeException | XPathException e) {
            outputError(context, new StringBuilder(e.getMessage()), e);
        }
        return randomInt != null
                ? Int64Value.makeIntegerValue(randomInt)
                : EmptyAtomicSequence.getInstance();
    }
}
