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

import stroom.ai.api.SimpleTokenCountEstimator;

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

@XsltFunctionDef(
        name = SplitDocument.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.OTHER,
        commonSubCategories = {"AI"},
        commonDescription = """
                Splits text into overlapping segments for language-model tokenisation. The segment and overlap sizes are
                estimated token counts.
                """,
        commonReturnType = XsltDataType.SEQUENCE,
        commonReturnDescription = "An array of text segments, or an empty array if splitting fails.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "doc",
                                        description = "The document text to split.",
                                        argType = XsltDataType.STRING
                                ),
                                @XsltFunctionArg(
                                        name = "segmentSize",
                                        description = "The target segment size in estimated tokens.",
                                        argType = XsltDataType.DECIMAL
                                ),
                                @XsltFunctionArg(
                                        name = "overlapSize",
                                        description = "The overlap between adjacent segments in estimated tokens.",
                                        argType = XsltDataType.DECIMAL
                                )
                        }
                )
        })
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
