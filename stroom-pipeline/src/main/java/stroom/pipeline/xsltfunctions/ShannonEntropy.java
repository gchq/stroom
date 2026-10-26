/*
 * Copyright 2026 Crown Copyright
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

import stroom.query.language.functions.ShannonEntropyCalculator;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.DoubleValue;

@XsltFunctionDef(
        name = ShannonEntropy.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.STRING,
        commonDescription = """
                Calculates the Shannon entropy of a string, i.e. how unpredictable its characters are.
                Useful for spotting machine-generated values such as random domain names, encoded command
                lines or tokens, and for storing the result as an index field at ingest.

                The result ranges from `0`, for one repeated character, up to log2 of the string's length,
                so short strings cannot score highly, e.g. a 12-character string scores at most `3.585`.
                Random-looking strings typically score about `3.5` or above.

                Characters are counted as Unicode code points and are case-sensitive; use
                `stroom:shannon-entropy(lower-case($value))` to ignore case.
                This gives the same result as the `shannonEntropy()` expression function.
                """,
        commonReturnType = XsltDataType.DECIMAL,
        commonReturnDescription = "The Shannon entropy of the string in bits per character, or an empty " +
                                  "sequence if there is no input.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "value",
                                        description = "The string to measure.",
                                        argType = XsltDataType.STRING
                                )
                        }
                )
        })
class ShannonEntropy extends StroomExtensionFunctionCall {

    /// The name of the function in XSLT, without the `stroom:` prefix.
    public static final String FUNCTION_NAME = "shannon-entropy";

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments)
            throws XPathException {

        final String value = getSafeString(functionName, context, arguments, 0);
        if (value == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return new DoubleValue(ShannonEntropyCalculator.calculate(value));
    }
}
