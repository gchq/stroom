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
import net.sf.saxon.value.StringValue;

@XsltFunctionDef(
        name = Get.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Reads a value stored under a key by `put()` during the current pipeline process.

                Values are stored against a key name so that multiple values can be stored.
                These functions can be used for many purposes but are most commonly used to count a number
                of records that meet certain criteria.

                The map is in the scope of the current pipeline process so values do not live after the stream
                has been processed.
                Also, the map will only contain entries that were `put()` within the current pipeline process.
                """,
        commonReturnType = XsltDataType.STRING,
        commonReturnDescription = "The stored value, or an empty sequence if the key is absent.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "key",
                                        description = "The key used when storing the value.",
                                        argType = XsltDataType.STRING
                                )
                        }
                )
        })
class Get extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "get";

    private final TaskScopeMap map;

    @Inject
    Get(final TaskScopeMap map) {
        this.map = map;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            final String key = getSafeString(functionName, context, arguments, 0);
            result = map.get(key);
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
