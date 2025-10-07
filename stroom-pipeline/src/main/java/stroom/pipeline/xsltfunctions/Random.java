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
import net.sf.saxon.value.DoubleValue;

@XsltFunctionDef(
        name = Random.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.VALUE,
        commonDescription = "Generates a random number greater than 0.0 and less than 1.0.",
        commonReturnType = XsltDataType.DECIMAL,
        commonReturnDescription = "The random number.",
        signatures = {
                @XsltFunctionSignature(
                        args = {})
        })
class Random extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "random";

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        try {
            return new DoubleValue(Math.random());
        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append(e.getMessage());
            outputWarning(context, sb, e);
        }

        return EmptyAtomicSequence.getInstance();
    }
}
