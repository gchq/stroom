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

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomScope;
import stroom.xml.event.np.NPEventList;

@Component
@Scope(StroomScope.PROTOTYPE)
public class Lookup extends AbstractLookup {
    @Override
    protected Sequence doLookup(final XPathContext context, final String map, final String key, final long eventTime,
                                final boolean ignoreWarnings, final StringBuilder lookupIdentifier) throws XPathException {
        final SequenceMaker sequenceMaker = new SequenceMaker(context);
        final NPEventList eventList = (NPEventList) getReferenceData(map, key, eventTime, lookupIdentifier);
        if (eventList != null) {
            sequenceMaker.open();
            sequenceMaker.consume(eventList);
            sequenceMaker.close();
        } else if (!ignoreWarnings) {
            createLookupFailWarning(context, map, key, eventTime, null);
        }

        return sequenceMaker.toSequence();
    }
}
