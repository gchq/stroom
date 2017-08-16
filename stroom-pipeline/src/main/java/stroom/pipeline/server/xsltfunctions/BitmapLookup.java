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
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomScope;
import stroom.xml.event.np.NPEventList;

@Component
@Scope(StroomScope.PROTOTYPE)
public class BitmapLookup extends AbstractLookup {
    @Override
    protected Sequence doLookup(final XPathContext context, final String map, final String key, final long eventTime,
                                final boolean ignoreWarnings, final StringBuilder lookupIdentifier) throws XPathException {
        SequenceMaker sequenceMaker = null;

        int val = 0;
        try {
            if (key.startsWith("0x")) {
                val = Integer.valueOf(key.substring(2), 16);
            } else {
                val = Integer.valueOf(key);
            }
        } catch (final NumberFormatException e) {
            throw new NumberFormatException("unable to parse number '" + key + "'");
        }

        final int[] bits = Bitmap.getBits(val);
        StringBuilder failedBits = null;

        if (bits.length > 0) {
            for (final int bit : bits) {
                final String k = String.valueOf(bit);
                final NPEventList eventList = (NPEventList) getReferenceData(map, k, eventTime, lookupIdentifier);
                if (eventList != null) {
                    if (sequenceMaker == null) {
                        sequenceMaker = new SequenceMaker(context);
                        sequenceMaker.open();
                    }
                    sequenceMaker.consume(eventList);
                } else if (!ignoreWarnings) {
                    if (failedBits == null) {
                        failedBits = new StringBuilder();
                    }
                    failedBits.append(k);
                    failedBits.append(",");
                }
            }

            if (failedBits != null) {
                failedBits.setLength(failedBits.length() - 1);
                failedBits.insert(0, "{");
                failedBits.append("}");
                createLookupFailWarning(context, map, failedBits.toString(), eventTime, null);
            }

            if (sequenceMaker != null) {
                sequenceMaker.close();
                return sequenceMaker.toSequence();
            }
        }

        return EmptyAtomicSequence.getInstance();
    }
}
