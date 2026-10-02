/*
 * Copyright 2021 Crown Copyright
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

import stroom.meta.shared.Meta;
import stroom.pipeline.state.MetaHolder;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;

@XsltFunctionDef(
        name = SourceId.FUNCTION_NAME,
        aliases = {SourceId.FUNCTION_NAME_STREAM_ID},
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Returns the ID of the current input stream. `stream-id()` is an alias retained for compatibility.
                """,
        commonReturnType = XsltDataType.STRING,
        commonReturnDescription = "The current input stream ID, if available.",
        signatures = {
                @XsltFunctionSignature(
                        args = {}
                )
        })
class SourceId extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "source-id";
    public static final String FUNCTION_NAME_STREAM_ID = "stream-id";

    private final MetaHolder metaHolder;

    @Inject
    SourceId(final MetaHolder metaHolder) {
        this.metaHolder = metaHolder;
    }

    @Override
    protected Sequence call(final String functionName,
                            final XPathContext context,
                            final Sequence[] arguments) {
        String result = null;

        try {
            final Meta meta = metaHolder.getMeta();
            if (meta != null) {
                result = String.valueOf(meta.getId());
            }
        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
