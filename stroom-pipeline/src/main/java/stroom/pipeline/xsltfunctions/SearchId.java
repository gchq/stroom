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

import stroom.pipeline.state.SearchIdHolder;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;

@XsltFunctionDef(
        name = SearchId.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Returns the batch search ID when a pipeline is processing as part of a batch search.
                """,
        commonReturnType = XsltDataType.STRING,
        commonReturnDescription = "The batch search ID, if available.",
        signatures = {
                @XsltFunctionSignature(
                        args = {}
                )
        })
class SearchId extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "search-id";

    private final SearchIdHolder searchIdHolder;

    @Inject
    SearchId(final SearchIdHolder searchIdHolder) {
        this.searchIdHolder = searchIdHolder;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            result = searchIdHolder.getSearchId();
        } catch (final RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
