package stroom.pipeline.xsltfunctions;

import stroom.langchain.api.SimpleTokenCountEstimator;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class SplitDocument extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "split-document";

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        List<String> result = null;

        try {
            final String doc = getSafeString(functionName, context, arguments, 0);
            final String segmentSize = getSafeString(functionName, context, arguments, 1);
            final String overlapSize = getSafeString(functionName, context, arguments, 2);

            final SimpleTokenCountEstimator estimator = new SimpleTokenCountEstimator();
            result = DocumentSplitters
                    .recursive(Integer.parseInt(segmentSize), Integer.parseInt(overlapSize), estimator)
                    .split(Document.from(doc))
                    .stream().map(TextSegment::text)
                    .toList();
        } catch (final XPathException | RuntimeException e) {
            outputError(context, new StringBuilder(e.getMessage()), e);
        }

        if (result == null) {
            return new SimpleArrayItem(new ArrayList<>());
        } else {
            return new SimpleArrayItem(result.stream()
                    .map(StringValue::makeStringValue)
                    .collect(Collectors.toList()));
        }
    }
}
