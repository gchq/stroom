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

package stroom.pipeline.xml.event.simple;

import org.xml.sax.Attributes;

import java.io.Serializable;

public class AttributesImpl implements Attributes, Serializable {

    private static final long serialVersionUID = 8341093893787192467L;

    private final int length;
    private final String[] data;

    public AttributesImpl() {
        length = 0;
        data = null;
    }

    /**
     * Copy an existing Attributes object.
     * <p>
     * <p>
     * This constructor is especially useful inside a
     * {@link org.xml.sax.ContentHandler#startElement startElement} event.
     * </p>
     *
     * @param atts The existing Attributes object.
     */
    public AttributesImpl(final Attributes atts) {
        length = atts.getLength();
        if (length > 0) {
            data = new String[length * 5];
            for (int i = 0; i < length; i++) {
                data[i * 5] = atts.getURI(i);
                data[i * 5 + 1] = atts.getLocalName(i);
                data[i * 5 + 2] = atts.getQName(i);
                data[i * 5 + 3] = atts.getType(i);
                data[i * 5 + 4] = atts.getValue(i);
            }
        } else {
            data = null;
        }
    }

    // //////////////////////////////////////////////////////////////////
    // Implementation of org.xml.sax.Attributes.
    // //////////////////////////////////////////////////////////////////

    /**
     * Return the number of attributes in the list.
     *
     * @return The number of attributes in the list.
     * @see org.xml.sax.Attributes#getLength
     */
    @Override
    public int getLength() {
        return length;
    }

    /**
     * Return an attribute's Namespace URI.
     *
     * @param index The attribute's index (zero-based).
     * @return The Namespace URI, the empty string if none is available, or null
     * if the index is out of range.
     * @see org.xml.sax.Attributes#getURI
     */
    @Override
    public String getURI(final int index) {
        if (index >= 0 && index < length) {
            return data[index * 5];
        } else {
            return null;
        }
    }

    /**
     * Return an attribute's local name.
     *
     * @param index The attribute's index (zero-based).
     * @return The attribute's local name, the empty string if none is
     * available, or null if the index if out of range.
     * @see org.xml.sax.Attributes#getLocalName
     */
    @Override
    public String getLocalName(final int index) {
        if (index >= 0 && index < length) {
            return data[index * 5 + 1];
        } else {
            return null;
        }
    }

    /**
     * Return an attribute's qualified (prefixed) name.
     *
     * @param index The attribute's index (zero-based).
     * @return The attribute's qualified name, the empty string if none is
     * available, or null if the index is out of bounds.
     * @see org.xml.sax.Attributes#getQName
     */
    @Override
    public String getQName(final int index) {
        if (index >= 0 && index < length) {
            return data[index * 5 + 2];
        } else {
            return null;
        }
    }

    /**
     * Return an attribute's type by index.
     *
     * @param index The attribute's index (zero-based).
     * @return The attribute's type, "CDATA" if the type is unknown, or null if
     * the index is out of bounds.
     * @see org.xml.sax.Attributes#getType(int)
     */
    @Override
    public String getType(final int index) {
        if (index >= 0 && index < length) {
            return data[index * 5 + 3];
        } else {
            return null;
        }
    }

    /**
     * Return an attribute's value by index.
     *
     * @param index The attribute's index (zero-based).
     * @return The attribute's value or null if the index is out of bounds.
     * @see org.xml.sax.Attributes#getValue(int)
     */
    @Override
    public String getValue(final int index) {
        if (index >= 0 && index < length) {
            return data[index * 5 + 4];
        } else {
            return null;
        }
    }

    /**
     * Look up an attribute's index by Namespace name.
     * <p>
     * <p>
     * In many cases, it will be more efficient to look up the name once and use
     * the index query methods rather than using the name query methods
     * repeatedly.
     * </p>
     *
     * @param uri       The attribute's Namespace URI, or the empty string if none is
     *                  available.
     * @param localName The attribute's local name.
     * @return The attribute's index, or -1 if none matches.
     * @see org.xml.sax.Attributes#getIndex(java.lang.String, java.lang.String)
     */
    @Override
    public int getIndex(final String uri, final String localName) {
        final int max = length * 5;
        for (int i = 0; i < max; i += 5) {
            if (data[i].equals(uri) && data[i + 1].equals(localName)) {
                return i / 5;
            }
        }
        return -1;
    }

    /**
     * Look up an attribute's index by qualified (prefixed) name.
     *
     * @param qName The qualified name.
     * @return The attribute's index, or -1 if none matches.
     * @see org.xml.sax.Attributes#getIndex(java.lang.String)
     */
    @Override
    public int getIndex(final String qName) {
        final int max = length * 5;
        for (int i = 0; i < max; i += 5) {
            if (data[i + 2].equals(qName)) {
                return i / 5;
            }
        }
        return -1;
    }

    /**
     * Look up an attribute's type by Namespace-qualified name.
     *
     * @param uri       The Namespace URI, or the empty string for a name with no
     *                  explicit Namespace URI.
     * @param localName The local name.
     * @return The attribute's type, or null if there is no matching attribute.
     * @see org.xml.sax.Attributes#getType(java.lang.String, java.lang.String)
     */
    @Override
    public String getType(final String uri, final String localName) {
        final int max = length * 5;
        for (int i = 0; i < max; i += 5) {
            if (data[i].equals(uri) && data[i + 1].equals(localName)) {
                return data[i + 3];
            }
        }
        return null;
    }

    /**
     * Look up an attribute's type by qualified (prefixed) name.
     *
     * @param qName The qualified name.
     * @return The attribute's type, or null if there is no matching attribute.
     * @see org.xml.sax.Attributes#getType(java.lang.String)
     */
    @Override
    public String getType(final String qName) {
        final int max = length * 5;
        for (int i = 0; i < max; i += 5) {
            if (data[i + 2].equals(qName)) {
                return data[i + 3];
            }
        }
        return null;
    }

    /**
     * Look up an attribute's value by Namespace-qualified name.
     *
     * @param uri       The Namespace URI, or the empty string for a name with no
     *                  explicit Namespace URI.
     * @param localName The local name.
     * @return The attribute's value, or null if there is no matching attribute.
     * @see org.xml.sax.Attributes#getValue(java.lang.String, java.lang.String)
     */
    @Override
    public String getValue(final String uri, final String localName) {
        final int max = length * 5;
        for (int i = 0; i < max; i += 5) {
            if (data[i].equals(uri) && data[i + 1].equals(localName)) {
                return data[i + 4];
            }
        }
        return null;
    }

    /**
     * Look up an attribute's value by qualified (prefixed) name.
     *
     * @param qName The qualified name.
     * @return The attribute's value, or null if there is no matching attribute.
     * @see org.xml.sax.Attributes#getValue(java.lang.String)
     */
    @Override
    public String getValue(final String qName) {
        final int max = length * 5;
        for (int i = 0; i < max; i += 5) {
            if (data[i + 2].equals(qName)) {
                return data[i + 4];
            }
        }
        return null;
    }
}
