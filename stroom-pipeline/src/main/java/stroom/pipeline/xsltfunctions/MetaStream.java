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
import net.sf.saxon.om.Sequence;

import java.util.Objects;

@XsltFunctionDef(
        name = MetaStream.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Returns metadata for the current stream and part as an XML document in the `stroom-meta` namespace.
                The document has a `meta-stream` root element and a `string` element for each metadata entry,
                with its name in the `key` attribute. The entries are ordered by name.

                If the stream part cannot be read, an empty `meta-stream` element is returned.
                """,
        commonReturnType = XsltDataType.SEQUENCE,
        commonReturnDescription = "An XML document containing the stream part's metadata.",
        signatures = {
                @XsltFunctionSignature(
                        args = {})
        })
public class MetaStream extends AbstractMetaStream {

    public static final String FUNCTION_NAME = "meta-stream";

    @Inject
    MetaStream(final AttributeMapFactory attributeMapFactory, final MetaHolder metaHolder) {
        super(attributeMapFactory, metaHolder);
    }

    @Override
    protected Sequence call(final String functionName,
                            final XPathContext context,
                            final Sequence[] arguments) {

        final long streamId = Objects.requireNonNull(
                metaHolder.getMetaId(),
                "meta is null in the metaHolder");
        final long partNo = metaHolder.getPartNo();
        return doCall(functionName, context, streamId, partNo);
    }
}
