package stroom.pipeline.xsltfunctions;

import stroom.data.store.api.DataService;
import stroom.pipeline.state.MetaHolder;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.util.Map;

public class MetaAttribute extends StroomExtensionFunctionCall {

    private final MetaHolder metaHolder;
    private final DataService dataService;

    private Map<String, String> metaAttributes = null;

    @Inject
    MetaAttribute(final MetaHolder metaHolder,
                  final DataService dataService) {
        this.metaHolder = metaHolder;
        this.dataService = dataService;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            String key = null;
            try {
                key = getSafeString(functionName, context, arguments, 0);

                if (metaAttributes == null) {
                    metaAttributes = dataService.metaAttributes(metaHolder.getMeta().getId());
                }
                result = metaAttributes.get(key);
            } catch (final XPathException | RuntimeException e) {
                outputWarning(context, new StringBuilder("Error fetching meta attribute for key '" + key + "'"), e);
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
