/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.parser.Location;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

public abstract class EventListProxyConsumer {

    static final Location NULL_LOCATION = new NullLocation();
    static final String EMPTY = "";
    private final XPathContext context;

    EventListProxyConsumer(final XPathContext context) {
        this.context = context;
    }

    static void startDocument(final Receiver receiver,
                              final PipelineConfiguration pipelineConfiguration) throws XPathException {
        receiver.setPipelineConfiguration(pipelineConfiguration);
        receiver.open();
        receiver.startDocument(0);
    }

    static void endDocument(final Receiver receiver) throws XPathException {
        receiver.endDocument();
        receiver.close();
    }

    PipelineConfiguration buildPipelineConfiguration() {
        final Configuration configuration = context.getConfiguration();
        return configuration.makePipelineConfiguration();
    }

    public abstract Sequence map(RefDataValueProxy refDataValueProxy);

    private static class NullLocation implements Location {
        @Override
        public String getSystemId() {
            return null;
        }

        @Override
        public String getPublicId() {
            return null;
        }

        @Override
        public int getLineNumber() {
            return 0;
        }

        @Override
        public int getColumnNumber() {
            return 0;
        }

        @Override
        public Location saveLocation() {
            return this;
        }
    }
}
