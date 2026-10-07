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

import stroom.feed.api.FeedProperties;
import stroom.pipeline.state.FeedHolder;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;

@XsltFunctionDef(
        name = Classification.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Returns the display classification of the feed for the data being processed.
                """,
        commonReturnType = XsltDataType.STRING,
        commonReturnDescription = "The feed's display classification, if available.",
        signatures = {
                @XsltFunctionSignature(
                        args = {}
                )
        })
class Classification extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "classification";

    private final FeedHolder feedHolder;
    private final FeedProperties feedProperties;

    private String feedName;
    private String classification;

    @Inject
    Classification(final FeedHolder feedHolder,
                   final FeedProperties feedProperties) {
        this.feedHolder = feedHolder;
        this.feedProperties = feedProperties;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            if (feedName == null || !feedName.equals(feedHolder.getFeedName())) {
                feedName = feedHolder.getFeedName();
                classification = feedProperties.getDisplayClassification(feedName);
            }

            result = classification;
        } catch (final RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return NullSafe.getOrElseGet(
                result,
                StringValue::makeStringValue,
                EmptyAtomicSequence::getInstance);
    }
}
