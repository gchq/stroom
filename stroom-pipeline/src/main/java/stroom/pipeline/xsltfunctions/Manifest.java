package stroom.pipeline.xsltfunctions;

import stroom.data.store.api.DataException;
import stroom.data.store.api.DataService;
import stroom.pipeline.state.MetaHolder;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.SAXException;

import java.util.Map;
import java.util.Set;

public class Manifest extends StroomExtensionMetaFunctionCall {

    private final DataService dataService;
    private final MetaHolder metaHolder;

    public static final String FUNCTION_NAME_FOR_ID = "manifest-for-id";
    public static final String FUNCTION_NAME_NO_ARGS = "manifest";

    private static final String ELEMENT_NAME = "manifest";

    private long lastStreamId = -1;
    private Sequence metaSequence = null;

    @Inject
    Manifest(final DataService dataService, final MetaHolder metaHolder) {
        this.dataService = dataService;
        this.metaHolder = metaHolder;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        Sequence result = null;

        long streamId = metaHolder.getMeta().getId();

        try {
            if (arguments.length == 1) {
                streamId = Long.parseLong(getSafeString(functionName, context, arguments, 0));
            }
            result = getMetaSequence(context, streamId);
        } catch (final XPathException | SAXException | RuntimeException e) {
            outputWarning(context, new StringBuilder("Error fetching manifest for streamId " + streamId), e);
        }

        if (result == null) {
            result = EmptyAtomicSequence.getInstance();
        }

        return result;
    }

    private Sequence getMetaSequence(final XPathContext context, final long streamId) throws SAXException {
        if (metaSequence == null || streamId != lastStreamId) {
            try {
                lastStreamId = streamId;
                final Map<String, String> metaAttributes = dataService.metaAttributes(streamId);
                metaSequence = createMetaSequence(context, ELEMENT_NAME, metaAttributes.entrySet());
            } catch (final DataException e) {
                // if there are any problems getting the meta then return an empty sequence
                return createMetaSequence(context, ELEMENT_NAME, Set.of());
            }
        }
        return metaSequence;
    }
}
