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

import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

@XsltFunctionDef(
        name = Put.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Stores a string under a key for retrieval by `get()` during the current pipeline process.

                Values are stored against a key name so that multiple values can be stored.
                These functions can be used for many purposes but are most commonly used to count a number
                of records that meet certain criteria.

                The map is in the scope of the current pipeline process so values do not live after the stream
                has been processed.
                Also, the map will only contain entries that were `put()` within the current pipeline process.
                """,
        commonReturnType = XsltDataType.EMPTY_SEQUENCE,
        commonReturnDescription = "An empty sequence.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "key",
                                        description = "The key under which to store the value.",
                                        argType = XsltDataType.STRING
                                ),
                                @XsltFunctionArg(
                                        name = "value",
                                        description = "The value to store.",
                                        argType = XsltDataType.STRING
                                )
                        }
                )
        })
class Put extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "put";

    private final TaskScopeMap map;

    @Inject
    Put(final TaskScopeMap map) {
        this.map = map;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        try {
            final String key = getSafeString(functionName, context, arguments, 0);
            final String value = getSafeString(functionName, context, arguments, 1);
            map.put(key, value);
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return EmptyAtomicSequence.getInstance();
    }
}
