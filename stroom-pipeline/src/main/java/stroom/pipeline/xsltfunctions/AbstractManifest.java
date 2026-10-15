/*
 * Copyright 2026 Crown Copyright
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


import stroom.data.store.api.DataException;
import stroom.data.store.api.DataService;
import stroom.pipeline.state.MetaHolder;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

import java.util.Map;

public abstract class AbstractManifest extends StroomExtensionMetaFunctionCall {

    protected static final String ELEMENT_NAME = "manifest";
    protected final DataService dataService;
    protected final MetaHolder metaHolder;
    private long lastStreamId = -1;
    private Sequence metaSequence = null;

    public AbstractManifest(final DataService dataService, final MetaHolder metaHolder) {
        this.dataService = dataService;
        this.metaHolder = metaHolder;
    }

    @Override
    protected abstract Sequence call(String functionName, XPathContext context, Sequence[] arguments);

    @Nullable
    protected Sequence getMetaSequence(final XPathContext context, final long streamId) {
        Sequence result = null;
        try {
            if (metaSequence == null || streamId != lastStreamId) {
                lastStreamId = streamId;
                final Map<String, String> metaAttributes = dataService.metaAttributes(streamId);
                metaSequence = createMetaSequence(context, ELEMENT_NAME, metaAttributes.entrySet());
            }
            result = metaSequence;
        } catch (final SAXException | DataException e) {
            final StringBuilder sb = new StringBuilder(
                    "Error fetching manifest for streamId ").append(streamId);
            outputWarning(context, sb, e);
        }
        return result;
    }
}
