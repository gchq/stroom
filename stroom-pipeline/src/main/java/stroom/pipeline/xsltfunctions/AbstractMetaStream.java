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


import stroom.data.store.api.AttributeMapFactory;
import stroom.meta.api.AttributeMap;
import stroom.pipeline.state.MetaHolder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import java.io.UncheckedIOException;
import java.util.Objects;

public abstract class AbstractMetaStream extends StroomExtensionMetaFunctionCall {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractMetaStream.class);

    private static final String ELEMENT_NAME = "meta-stream";
    protected final AttributeMapFactory attributeMapFactory;
    protected final MetaHolder metaHolder;
    private long lastStreamId = -1;
    private long lastPartNo = -1;
    private Sequence metaSequence = null;

    public AbstractMetaStream(final AttributeMapFactory attributeMapFactory, final MetaHolder metaHolder) {
        this.attributeMapFactory = attributeMapFactory;
        this.metaHolder = metaHolder;
    }

    @Override
    protected abstract Sequence call(final String functionName,
                                     final XPathContext context,
                                     final Sequence[] arguments);

    protected Sequence doCall(final String functionName,
                              final XPathContext context,
                              final long streamId,
                              final long partNo) {
        LOGGER.debug("doCall(), functionName: {}, streamId: {}, partNo: {}", functionName, streamId, partNo);
        final Sequence result = getMetaSequence(context, streamId, partNo);

        return Objects.requireNonNullElseGet(result, () ->
                createEmptyMetaSequence(context, ELEMENT_NAME));
    }

    @Nullable
    private Sequence getMetaSequence(final XPathContext context, final long streamId, final long partNo) {
        Sequence result = null;

        try {
            if (metaSequence == null || lastStreamId != streamId || lastPartNo != partNo) {
                lastStreamId = streamId;
                lastPartNo = partNo;
                final AttributeMap attributeMap = attributeMapFactory.getAttributeMapForPart(streamId, partNo);
                metaSequence = createMetaSequence(context, ELEMENT_NAME, attributeMap.entrySet());
            }
            result = metaSequence;
        } catch (final SAXException | UncheckedIOException e) {
            final StringBuilder sb = new StringBuilder("Error fetching meta stream for streamId ")
                    .append(streamId)
                    .append(" and partNo ")
                    .append(partNo);
            outputWarning(context, sb, e);
        }
        return result;
    }
}
