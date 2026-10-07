/*
 * Copyright 2025 Crown Copyright
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

import stroom.data.store.api.AttributeMapFactory;
import stroom.pipeline.state.MetaHolder;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

import java.util.Arrays;
import java.util.Objects;

@XsltFunctionDef(
        name = MetaStreamForId.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Returns metadata for the specified streamId and partNo as an XML document in the
                `stroom-meta` namespace.
                The document has a `meta-stream` root element and a `string` element for each metadata entry,
                with its name in the `key` attribute. The entries are ordered by name.

                If the stream part cannot be read, an empty `meta-stream` element is returned.
                """,
        commonReturnType = XsltDataType.SEQUENCE,
        commonReturnDescription = "An XML document containing the stream part's metadata.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "streamId",
                                        description = "The ID of the stream containing the part.",
                                        argType = XsltDataType.INTEGER),
                                @XsltFunctionArg(
                                        name = "partNo",
                                        description = "The part number within the stream, starting at `1`.",
                                        argType = XsltDataType.INTEGER)
                        })
        })
public class MetaStreamForId extends AbstractMetaStream {

    public static final String FUNCTION_NAME = "meta-stream-for-id";

    @Inject
    MetaStreamForId(final AttributeMapFactory attributeMapFactory, final MetaHolder metaHolder) {
        super(attributeMapFactory, metaHolder);
    }

    @Override
    protected Sequence call(final String functionName,
                            final XPathContext context,
                            final Sequence[] arguments) {
        Sequence result = null;
        try {
            final long streamId = Long.parseLong(getSafeString(functionName, context, arguments, 0));
            final long partNo = Long.parseLong(getSafeString(functionName, context, arguments, 1));
            result = doCall(functionName, context, streamId, partNo);
        } catch (final XPathException e) {
            final StringBuilder sb = new StringBuilder("Error parsing arguments '")
                    .append(Arrays.toString(arguments))
                    .append("'");
            outputWarning(context, sb, e);
        }
        return Objects.requireNonNullElseGet(result, EmptyAtomicSequence::getInstance);
    }
}
