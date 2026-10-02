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

import stroom.pipeline.state.MetaDataHolder;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

@XsltFunctionDef(
        name = Meta.FUNCTION_NAME,
        aliases = {Meta.FUNCTION_NAME_FEED_ATTRIBUTE},
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Returns a metadata value for the current stream part. `feed-attribute()` is a deprecated alias.
                """,
        commonReturnType = XsltDataType.STRING,
        commonReturnDescription = "The value for the key, or an empty sequence if it is absent.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "key",
                                        description = "The metadata key, such as `Feed`, `StreamType` or a " +
                                                "supplied attribute.",
                                        argType = XsltDataType.STRING
                                )
                        }
                )
        })
class Meta extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "meta";
    public static final String FUNCTION_NAME_FEED_ATTRIBUTE = "feed-attribute";

    private final MetaDataHolder metaDataHolder;

    @Inject
    Meta(final MetaDataHolder metaDataHolder) {
        this.metaDataHolder = metaDataHolder;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            String key = null;
            try {
                key = getSafeString(functionName, context, arguments, 0);
                result = metaDataHolder.get(key);
            } catch (final XPathException | RuntimeException e) {
                outputWarning(context, new StringBuilder("Error fetching meta value for key '" + key + "'"), e);
            }
        } catch (final RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
