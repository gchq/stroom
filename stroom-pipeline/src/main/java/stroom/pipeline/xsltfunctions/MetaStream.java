package stroom.pipeline.xsltfunctions;

import stroom.data.store.api.AttributeMapFactory;
import stroom.meta.api.AttributeMap;
import stroom.pipeline.state.MetaHolder;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.SAXException;

import java.io.UncheckedIOException;
import java.util.Set;


public class MetaStream extends StroomExtensionMetaFunctionCall {

    private final AttributeMapFactory attributeMapFactory;
    private final MetaHolder metaHolder;

    public static final String FUNCTION_NAME_FOR_ID = "meta-stream-for-id";
    public static final String FUNCTION_NAME_NO_ARGS = "meta-stream";

    private static final String ELEMENT_NAME = "meta-stream";

    private long lastStreamId = -1;
    private long lastPartNo = -1;
    private Sequence metaSequence = null;

    @Inject
    MetaStream(final AttributeMapFactory attributeMapFactory, final MetaHolder metaHolder) {
        this.attributeMapFactory = attributeMapFactory;
        this.metaHolder = metaHolder;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        Sequence result = null;

        long streamId = metaHolder.getMeta().getId();
        long partNo = metaHolder.getPartIndex();

        try {
            if (arguments.length == 2) {
                streamId = Long.parseLong(getSafeString(functionName, context, arguments, 0));
                partNo = Long.parseLong(getSafeString(functionName, context, arguments, 1)) - 1;
            }

            result = getMetaSequence(context, streamId, partNo);
        } catch (final XPathException | SAXException | RuntimeException e) {
            outputWarning(context, new StringBuilder("Error fetching meta stream for streamId " + streamId), e);
        }

        if (result == null) {
            result = EmptyAtomicSequence.getInstance();
        }

        return result;
    }

    private Sequence getMetaSequence(final XPathContext context, final long streamId, final long partNo)
            throws SAXException {
        if (metaSequence == null || lastStreamId != streamId || lastPartNo != partNo) {
            try {
                lastStreamId = streamId;
                lastPartNo = partNo;
                final AttributeMap attributeMap = attributeMapFactory.getAttributeMapForPart(streamId, partNo);
                metaSequence = createMetaSequence(context, ELEMENT_NAME, attributeMap.entrySet());
            } catch (final UncheckedIOException ex) {
                // if there are any problems getting the meta then return an empty sequence
                return createMetaSequence(context, ELEMENT_NAME, Set.of());
            }
        }

        return metaSequence;
    }

}
