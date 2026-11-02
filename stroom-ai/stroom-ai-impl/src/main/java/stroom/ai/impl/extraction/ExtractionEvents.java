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

package stroom.ai.impl.extraction;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.extraction.ExtractionPlan.Sample;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolSession;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.io.StringReader;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// Reads what an extraction needs from the events of a table it is for, as the user, without stepping: the
/// namespace of an event the extraction was tried on, which the XSLT reads the events in. It is read when the user
/// clicks the extraction's button and again as it is written, so the XSLT is written for the events as they are,
/// not for a namespace the agent gave.
public class ExtractionEvents {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExtractionEvents.class);

    private final StreamReader streamReader;
    private final SecurityContext securityContext;
    private final AiAuditLog auditLog;

    @Inject
    public ExtractionEvents(final StreamReader streamReader,
                            final SecurityContext securityContext,
                            final AiAuditLog auditLog) {
        this.streamReader = streamReader;
        this.securityContext = securityContext;
        this.auditLog = auditLog;
    }

    /// Reads the namespace of an event: that of its stream's root element (e.g. `event-logging:3` for
    /// `<Events xmlns="event-logging:3">`), which an Events stream's events share.
    ///
    /// @param sample  The event.
    /// @param session The chat it is read for, to record that the user viewed the stream.
    /// @return The namespace, or blank for none.
    /// @throws IllegalStateException if the event can't be read, saying why.
    public String namespace(final Sample sample, final ToolSession session) {
        if (!securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            throw new IllegalStateException("You don't have permission to view data, so the events' namespace "
                                            + "can't be read for the extraction's XSLT.");
        }
        final String data;
        try {
            data = streamReader.fetch(sample.streamId(), sample.eventId() - 1, null).data();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read event " + sample, e);
            throw new IllegalStateException(unreadable(sample), e);
        }
        auditLog.streamsViewed(session, List.of(sample.streamId()), "Read the namespace of an event of stream "
                                                                    + sample.streamId() + " for a search "
                                                                    + "extraction");
        final String namespace = rootNamespace(NullSafe.isBlankString(data)
                ? null
                : StreamReader.rootClosed(data));
        if (namespace == null) {
            throw new IllegalStateException(unreadable(sample));
        }
        return namespace;
    }

    /// The namespace of an XML document's root element.
    ///
    /// @param xml The document.
    /// @return The namespace, blank for none; null if it can't be read.
    static String rootNamespace(final String xml) {
        if (xml == null) {
            return null;
        }
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            final XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        return NullSafe.string(reader.getNamespaceURI());
                    }
                }
            } finally {
                reader.close();
            }
        } catch (final XMLStreamException e) {
            return null;
        }
        return null;
    }

    private static String unreadable(final Sample sample) {
        return "Event " + sample.eventId() + " of stream " + sample.streamId() + ", which the extraction was tried "
               + "on, can't be read, so the events' namespace isn't known and nothing was written. Try the "
               + "extraction again with preview_extraction, and propose it again.";
    }
}
