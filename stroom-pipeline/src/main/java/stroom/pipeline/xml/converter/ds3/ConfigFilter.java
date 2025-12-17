/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.xml.converter.ds3;

import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.pipeline.xml.converter.ds3.GroupFactory.MatchOrder;
import stroom.pipeline.xml.converter.ds3.NodeFactory.NodeType;

import org.apache.commons.text.StringEscapeUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class ConfigFilter extends AbstractXMLFilter {

    private static final String XML_ELEMENT_DATA_SPLITTER = "dataSplitter";
    private static final String XML_ELEMENT_SPLIT = "split";
    private static final String XML_ELEMENT_REGEX = "regex";
    private static final String XML_ELEMENT_ALL = "all";
    private static final String XML_ELEMENT_GROUP = "group";
    private static final String XML_ELEMENT_VAR = "var";
    private static final String XML_ELEMENT_DATA = "data";

    private static final String XML_ATTRIBUTE_BUFFER_SIZE = "bufferSize";
    private static final String XML_ATTRIBUTE_IGNORE_ERRORS = "ignoreErrors";
    private static final String XML_ATTRIBUTE_MATCH_ORDER = "matchOrder";
    private static final String XML_ATTRIBUTE_REVERSE = "reverse";
    private static final String XML_ATTRIBUTE_MIN_MATCH = "minMatch";
    private static final String XML_ATTRIBUTE_MAX_MATCH = "maxMatch";
    private static final String XML_ATTRIBUTE_ONLY_MATCH = "onlyMatch";
    private static final String XML_ATTRIBUTE_ADVANCE = "advance";
    private static final String XML_ATTRIBUTE_PATTERN = "pattern";
    private static final String XML_ATTRIBUTE_DOT_ALL = "dotAll";
    private static final String XML_ATTRIBUTE_CASE_INSENSITIVE = "caseInsensitive";
    private static final String XML_ATTRIBUTE_DELIMITER = "delimiter";
    private static final String XML_ATTRIBUTE_ESCAPE = "escape";
    private static final String XML_ATTRIBUTE_CONTAINER_START = "containerStart";
    private static final String XML_ATTRIBUTE_CONTAINER_END = "containerEnd";
    private static final String XML_ATTRIBUTE_ID = "id";
    private static final String XML_ATTRIBUTE_NAME = "name";
    private static final String XML_ATTRIBUTE_VALUE = "value";

    private boolean inRoot;
    private Deque<NodeFactory> parentDeque;

    public ConfigFilter(final RootFactory factory) {
        parentDeque = new ArrayDeque<>();
        parentDeque.push(factory);
    }

    @Override
    public void startElement(final String uri,
                             final String localName,
                             final String qName,
                             final Attributes atts) throws SAXException {
        if (localName.equals(XML_ELEMENT_DATA_SPLITTER)) {
            inRoot = true;
            if (parentDeque.peek().getNodeType() == NodeType.ROOT) {
                final RootFactory rootFactory = (RootFactory) parentDeque.peek();
                rootFactory.setBufferSize(getInt(atts, XML_ATTRIBUTE_BUFFER_SIZE, RootFactory.DEFAULT_BUFFER_SIZE));
                rootFactory.setIgnoreErrors(getIgnoreErorrs(atts));
            }

        } else if (!inRoot) {
            throw new SAXException("Unknown root element \"" + localName + "\"");

        } else if (localName.equals(XML_ELEMENT_SPLIT)) {
            final String id = atts.getValue(XML_ATTRIBUTE_ID);
            final String delimiter = unescape(atts.getValue(XML_ATTRIBUTE_DELIMITER));
            final String escape = atts.getValue(XML_ATTRIBUTE_ESCAPE);
            final String containerStart = atts.getValue(XML_ATTRIBUTE_CONTAINER_START);
            final String containerEnd = atts.getValue(XML_ATTRIBUTE_CONTAINER_END);

            final NodeFactory parent = parentDeque.peek();
            final NodeFactory node = new SplitFactory(parent, id, getMinMatch(atts), getMaxMatch(atts),
                    getOnlyMatch(atts), delimiter, escape, containerStart, containerEnd);
            parentDeque.push(node);

        } else if (localName.equals(XML_ELEMENT_REGEX)) {
            final String id = atts.getValue(XML_ATTRIBUTE_ID);
            final String pattern = atts.getValue(XML_ATTRIBUTE_PATTERN);
            final boolean caseInsensitive = getBool(atts, XML_ATTRIBUTE_CASE_INSENSITIVE, false);
            final boolean dotAll = getBool(atts, XML_ATTRIBUTE_DOT_ALL, false);
            final int advance = getInt(atts, XML_ATTRIBUTE_ADVANCE, -1);

            int flags = 0;
            if (caseInsensitive) {
                flags += Pattern.CASE_INSENSITIVE;
            }
            if (dotAll) {
                flags += Pattern.DOTALL;
            }

            final NodeFactory parent = parentDeque.peek();
            final NodeFactory node = new RegexFactory(parent, id, getMinMatch(atts), getMaxMatch(atts),
                    getOnlyMatch(atts), advance, pattern, flags);
            parentDeque.push(node);

        } else if (localName.equals(XML_ELEMENT_ALL)) {
            final String id = atts.getValue(XML_ATTRIBUTE_ID);

            final NodeFactory parent = parentDeque.peek();
            final NodeFactory node = new AllFactory(parent, id);
            parentDeque.push(node);

        } else if (localName.equals(XML_ELEMENT_GROUP)) {
            final String id = atts.getValue(XML_ATTRIBUTE_ID);
            final String value = atts.getValue(XML_ATTRIBUTE_VALUE);

            final NodeFactory parent = parentDeque.peek();
            final NodeFactory node = new GroupFactory(parent, id, value, getReverse(atts), getMatchOrder(atts),
                    getIgnoreErorrs(atts));
            parentDeque.push(node);

        } else if (localName.equals(XML_ELEMENT_VAR)) {
            final String id = atts.getValue(XML_ATTRIBUTE_ID);

            final NodeFactory parent = parentDeque.peek();
            final NodeFactory node = new VarFactory(parent, id);
            parentDeque.push(node);

        } else if (localName.equals(XML_ELEMENT_DATA)) {
            final String id = atts.getValue(XML_ATTRIBUTE_ID);
            final String name = atts.getValue(XML_ATTRIBUTE_NAME);
            final String value = atts.getValue(XML_ATTRIBUTE_VALUE);

            final NodeFactory parent = parentDeque.peek();
            final NodeFactory node = new DataFactory(parent, id, name, value);
            parentDeque.push(node);
        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        if (localName.equals(XML_ELEMENT_DATA_SPLITTER)) {
            inRoot = false;
        } else {
            parentDeque.pop();
        }

        super.endElement(uri, localName, qName);
    }

    private String unescape(final String string) {
        if (string == null) {
            return null;
        }

        return StringEscapeUtils.unescapeJava(string);
    }

    private boolean getIgnoreErorrs(final Attributes atts) throws SAXException {
        return getBool(atts, XML_ATTRIBUTE_IGNORE_ERRORS, false);
    }

    private MatchOrder getMatchOrder(final Attributes atts) throws SAXException {
        final String string = atts.getValue(XML_ATTRIBUTE_MATCH_ORDER);
        MatchOrder val = MatchOrder.SEQUENCE;
        if (string != null && string.equalsIgnoreCase(MatchOrder.ANY.toString())) {
            val = MatchOrder.ANY;
        }

        return val;
    }

    private boolean getReverse(final Attributes atts) throws SAXException {
        return getBool(atts, XML_ATTRIBUTE_REVERSE, false);
    }

    private int getMinMatch(final Attributes atts) throws SAXException {
        return getInt(atts, XML_ATTRIBUTE_MIN_MATCH, 0);
    }

    private int getMaxMatch(final Attributes atts) throws SAXException {
        return getInt(atts, XML_ATTRIBUTE_MAX_MATCH, -1);
    }

    private int getInt(final Attributes atts, final String attName, final int initial) throws SAXException {
        final String string = atts.getValue(attName);
        int val = initial;
        if (string != null) {
            try {
                val = Integer.parseInt(string);
            } catch (final NumberFormatException e) {
                throw new SAXException("Value for " + attName + " \"" + string + "\" is not a valid number");
            }
        }

        return val;
    }

    private boolean getBool(final Attributes atts, final String attName, final boolean initial) throws SAXException {
        final String string = atts.getValue(attName);
        boolean val = initial;
        if (string != null) {
            val = Boolean.parseBoolean(string);
        }

        return val;
    }

    private final Set<Integer> getOnlyMatch(final Attributes atts) throws SAXException {
        final String string = atts.getValue(XML_ATTRIBUTE_ONLY_MATCH);

        if (string != null && !string.isEmpty()) {
            // Turn the comma separated list into integers.
            final String[] arr = string.split(",");
            final Set<Integer> val = new HashSet<>(arr.length);
            for (int i = 0; i < arr.length; i++) {
                try {
                    final String str = arr[i];
                    val.add(Integer.parseInt(str));
                } catch (final NumberFormatException e) {
                    throw new SAXException("Value for " + XML_ATTRIBUTE_ONLY_MATCH + " \"" + string
                                           + "\" is not a valid comma separated list of numbers");
                }
            }

            return val;
        }

        return null;
    }
}
