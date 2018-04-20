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

package stroom.pipeline.filter;

import com.sun.xml.fastinfoset.sax.SAXDocumentSerializer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import stroom.entity.shared.Range;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.factory.ConfigurableElement;
import stroom.pipeline.factory.PipelineProperty;
import stroom.pipeline.shared.ElementIcons;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.refdata.MapStoreHolder;
import stroom.util.CharBuffer;
import stroom.util.shared.Severity;
import stroom.xml.event.EventList;
import stroom.xml.event.EventListBuilder;
import stroom.xml.event.EventListBuilderFactory;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;

/**
 * This XML filter captures XML content that defines key, value maps to be
 * stored as reference data. The key, value map content is likely to have been
 * produced as the result of an XSL transformation of some reference data.
 */
@ConfigurableElement(type = "ReferenceDataFilter", category = Category.FILTER, roles = {
        PipelineElementType.ROLE_TARGET, PipelineElementType.ROLE_HAS_TARGETS}, icon = ElementIcons.REFERENCE_DATA)
public class ReferenceDataFilter extends AbstractXMLFilter {
    private static final String REFERENCE_ELEMENT = "reference";
    private static final String MAP_ELEMENT = "map";
    private static final String KEY_ELEMENT = "key";
    private static final String FROM_ELEMENT = "from";
    private static final String TO_ELEMENT = "to";
    private static final String VALUE_ELEMENT = "value";

    private final MapStoreHolder mapStoreHolder;
    private final EventListInternPool internPool;
    private final ErrorReceiverProxy errorReceiverProxy;

    private final EventListBuilder handler = EventListBuilderFactory.createBuilder();
    private final SAXDocumentSerializer saxDocumentSerializer = new SAXDocumentSerializer();
    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    private final CharBuffer contentBuffer = new CharBuffer(20);

    private String map;
    private String key;
    private boolean inValue;
    private Long rangeFrom;
    private Long rangeTo;
    private boolean warnOnDuplicateKeys = false;
    private boolean overrideExistingValues = true;

    @Inject
    public ReferenceDataFilter(final MapStoreHolder mapStoreHolder,
                               final EventListInternPool internPool,
                               final ErrorReceiverProxy errorReceiverProxy) {
        this.mapStoreHolder = mapStoreHolder;
        this.internPool = internPool;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    /**
     * This method looks for a post processing function. If it finds one it does
     * not output the element. Instead it stores data about the function and
     * sets a flag so that the function can be performed when the corresponding
     * end element is reached.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @param atts      The element's attributes.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#startElement(java.lang.String,
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        contentBuffer.clear();

        if (VALUE_ELEMENT.equalsIgnoreCase(localName)) {
            inValue = true;
        } else if (inValue) {
            saxDocumentSerializer.startElement(uri, localName, qName, atts);
        }

        super.startElement(uri, localName, qName, atts);
    }

    /**
     * This method applies a post processing function if we are currently within
     * a function element. At this stage we should have details of the function
     * to apply from the corresponding start element and content to apply it to
     * from the characters event.
     *
     * @param uri       The element's Namespace URI, or the empty string.
     * @param localName The element's local name, or the empty string.
     * @param qName     The element's qualified (prefixed) name, or the empty string.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#endElement(java.lang.String,
     * java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (VALUE_ELEMENT.equalsIgnoreCase(localName)) {
            inValue = false;
        }

        if (inValue) {
            saxDocumentSerializer.endElement(uri, localName, qName);
        } else {
            if (MAP_ELEMENT.equalsIgnoreCase(localName)) {
                map = contentBuffer.toString();

            } else if (KEY_ELEMENT.equalsIgnoreCase(localName)) {
                key = contentBuffer.toString();

            } else if (FROM_ELEMENT.equalsIgnoreCase(localName)) {
                final String string = contentBuffer.toString();
                try {
                    rangeFrom = Long.parseLong(string);
                } catch (final RuntimeException e) {
                    errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                            "Unable to parse string \"" + string + "\" as long for range from", e);
                }

            } else if (TO_ELEMENT.equalsIgnoreCase(localName)) {
                final String string = contentBuffer.toString();
                try {
                    rangeTo = Long.parseLong(string);
                } catch (final RuntimeException e) {
                    errorReceiverProxy.log(Severity.ERROR, null, getElementId(),
                            "Unable to parse string \"" + string + "\" as long for range to", e);
                }

            } else if (REFERENCE_ELEMENT.equalsIgnoreCase(localName)) {
                EventList eventList = handler.getEventList();
                handler.reset();

                byte[] bEventList = byteArrayOutputStream.toByteArray();
                byteArrayOutputStream.reset();

                // TODO add bEventList to the OffHEapStore, getting a OffHeapStore.Key returned
                //

                // Intern the event list so we only have one identical copy in
                // memory.
                if (internPool != null) {
                    eventList = internPool.intern(eventList);
                }

                // Store the events.
                try {
                    if (map != null) {
                        if (key != null) {
                            mapStoreHolder.getMapStoreBuilder().setEvents(map, key, eventList, overrideExistingValues);
                        } else if (rangeFrom != null && rangeTo != null) {
                            if (rangeFrom > rangeTo) {
                                errorReceiverProxy
                                        .log(Severity.ERROR, null, getElementId(),
                                                "Range from '" + rangeFrom
                                                        + "' must be less than or equal to range to '" + rangeTo + "'",
                                                null);
                            } else if (rangeFrom.equals(rangeTo)) {
                                // If the range from and to are the same then we
                                // can treat this as a key
                                // rather than a range. This will improve lookup
                                // performance.
                                mapStoreHolder.getMapStoreBuilder().setEvents(map, rangeFrom.toString(), eventList,
                                        overrideExistingValues);
                            } else {
                                mapStoreHolder.getMapStoreBuilder().setEvents(map, new Range<>(rangeFrom, rangeTo),
                                        eventList, overrideExistingValues);
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    if (warnOnDuplicateKeys) {
                        errorReceiverProxy.log(Severity.WARNING, null, getElementId(), e.getMessage(), e);
                    }
                }

                // Set keys to null.
                map = null;
                key = null;
                rangeFrom = null;
                rangeTo = null;
            }
        }

        contentBuffer.clear();

        super.endElement(uri, localName, qName);
    }

    /**
     * If we are within a function element then this method should buffer the
     * character content so that it can be operated on in the function end
     * element.
     *
     * @param ch     An array of characters.
     * @param start  The starting position in the array.
     * @param length The number of characters to use from the array.
     * @throws org.xml.sax.SAXException The client may throw an exception during processing.
     * @see stroom.pipeline.filter.AbstractXMLFilter#characters(char[],
     * int, int)
     */
    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        if (inValue) {
            saxDocumentSerializer.characters(ch, start, length);
        } else {
            // outside the value element so capture the chars so we can get keys, map names, etc.
            contentBuffer.append(ch, start, length);
        }

        super.characters(ch, start, length);
    }

    @PipelineProperty(description = "Warn if there are duplicate keys found in the reference data?", defaultValue = "false")
    public void setWarnOnDuplicateKeys(final boolean warnOnDuplicateKeys) {
        this.warnOnDuplicateKeys = warnOnDuplicateKeys;
    }

    @PipelineProperty(description = "Allow duplicate keys to override existing values?", defaultValue = "true")
    public void setOverrideExistingValues(final boolean overrideExistingValues) {
        this.overrideExistingValues = overrideExistingValues;
    }
}
