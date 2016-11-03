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

package stroom.pipeline.server.xsltfunctions;

import javax.annotation.Resource;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import stroom.feed.shared.Feed;
import stroom.pipeline.state.FeedHolder;
import stroom.util.spring.StroomScope;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

@Component
@Scope(StroomScope.PROTOTYPE)
public class FeedName extends StroomExtensionFunctionCall {
    @Resource
    private FeedHolder feedHolder;

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        final Feed feed = feedHolder.getFeed();
        String feedName;
        if (feed == null) {
            feedName = "";
        } else {
            feedName = feed.getName();
        }

        return StringValue.makeStringValue(feedName);
    }
}
