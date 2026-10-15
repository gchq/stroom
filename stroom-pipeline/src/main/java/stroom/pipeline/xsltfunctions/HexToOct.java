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
import net.sf.saxon.value.StringValue;

@XsltFunctionDef(
        name = HexToOct.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.CONVERSION,
        commonDescription = """
                Converts a hexadecimal value to its octal representation.
                """,
        commonReturnType = XsltDataType.STRING,
        commonReturnDescription = "The octal representation, or an empty sequence if conversion fails.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "hex",
                                        description = "The hexadecimal value to convert.",
                                        argType = XsltDataType.STRING
                                )
                        }
                )
        })
class HexToOct extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "hex-to-oct";

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            final String hex = getSafeString(functionName, context, arguments, 0);
            final Long l = Long.parseLong(hex, 16);
            result = Long.toOctalString(l);
        } catch (final XPathException | RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append(e.getMessage());
            outputWarning(context, sb, e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
