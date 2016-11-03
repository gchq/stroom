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

package stroom.xml.event.np;

import java.io.Serializable;

import org.xml.sax.Attributes;

import net.sf.saxon.om.NamePool;

public class NPAttributes implements Attributes, Serializable {
    private static final long serialVersionUID = 8341093893787192467L;

    private static final String EMPTY = "";
    private static final String COMMA = ",";

    final NPEventListNamePool namePool;
    final int length;
    final int nameCode[];
    final String value[];
    private final int hash;

    /**
     * Copy an existing Attributes object.
     *
     * <p>
     * This constructor is especially useful inside a
     * {@link org.xml.sax.ContentHandler#startElement startElement} event.
     * </p>
     *
     * @param atts
     *            The existing Attributes object.
     */
    public NPAttributes(final NPEventListNamePool namePool, final Attributes atts) {
        this.namePool = namePool;

        if (atts != null) {
            length = atts.getLength();
            if (length > 0) {
                nameCode = new int[length];
                value = new String[length];

                for (int i = 0; i < length; i++) {
                    nameCode[i] = namePool.allocate(EMPTY, atts.getURI(i), atts.getLocalName(i));
                    value[i] = atts.getValue(i);
                }
            } else {
                value = null;
                nameCode = null;
            }
        } else {
            length = 0;
            nameCode = null;
            value = null;
        }

        // Build a hash code straight away as we will need it.
        int code = 31;
        code = code * 31 + length;
        if (nameCode == null) {
            code = code * 31;
        } else {
            code = code * 31 + nameCode.length;
            for (int i = 0; i < nameCode.length; i++) {
                code = code * 31 + nameCode[i];
            }
        }
        if (value == null) {
            code = code * 31;
        } else {
            code = code * 31 + value.length;
            for (int i = 0; i < value.length; i++) {
                if (value[i] == null) {
                    code = code * 31;
                } else {
                    code = code * 31 + value[i].hashCode();
                }
            }
        }
        hash = code;
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
     * @param index
     *            The attribute's index (zero-based).
     * @return The Namespace URI, the empty string if none is available, or null
     *         if the index is out of range.
     * @see org.xml.sax.Attributes#getURI
     */
    @Override
    public String getURI(final int index) {
        return namePool.getURI(nameCode[index]);
    }

    /**
     * Return an attribute's local name.
     *
     * @param index
     *            The attribute's index (zero-based).
     * @return The attribute's local name, the empty string if none is
     *         available, or null if the index if out of range.
     * @see org.xml.sax.Attributes#getLocalName
     */
    @Override
    public String getLocalName(final int index) {
        return namePool.getLocalName(nameCode[index]);
    }

    /**
     * Return an attribute's qualified (prefixed) name.
     *
     * @param index
     *            The attribute's index (zero-based).
     * @return The attribute's qualified name, the empty string if none is
     *         available, or null if the index is out of bounds.
     * @see org.xml.sax.Attributes#getQName
     */
    @Override
    public String getQName(final int index) {
        return namePool.getDisplayName(nameCode[index]);
    }

    /**
     * Return an attribute's type by index.
     *
     * @param index
     *            The attribute's index (zero-based).
     * @return The attribute's type, "CDATA" if the type is unknown, or null if
     *         the index is out of bounds.
     * @see org.xml.sax.Attributes#getType(int)
     */
    @Override
    public String getType(final int index) {
        throw new UnsupportedOperationException();
    }

    /**
     * Return an attribute's value by index.
     *
     * @param index
     *            The attribute's index (zero-based).
     * @return The attribute's value or null if the index is out of bounds.
     * @see org.xml.sax.Attributes#getValue(int)
     */
    @Override
    public String getValue(final int index) {
        return value[index];
    }

    /**
     * Look up an attribute's index by Namespace name.
     *
     * <p>
     * In many cases, it will be more efficient to look up the name once and use
     * the index query methods rather than using the name query methods
     * repeatedly.
     * </p>
     *
     * @param uri
     *            The attribute's Namespace URI, or the empty string if none is
     *            available.
     * @param localName
     *            The attribute's local name.
     * @return The attribute's index, or -1 if none matches.
     * @see org.xml.sax.Attributes#getIndex(java.lang.String,java.lang.String)
     */
    @Override
    public int getIndex(final String uri, final String localName) {
        final int fp = namePool.getFingerprint(uri, localName);
        for (int i = 0; i < length; i++) {
            if ((nameCode[i] & NamePool.FP_MASK) == fp) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Look up an attribute's index by qualified (prefixed) name.
     *
     * @param qName
     *            The qualified name.
     * @return The attribute's index, or -1 if none matches.
     * @see org.xml.sax.Attributes#getIndex(java.lang.String)
     */
    @Override
    public int getIndex(final String qName) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Look up an attribute's type by Namespace-qualified name.
     *
     * @param uri
     *            The Namespace URI, or the empty string for a name with no
     *            explicit Namespace URI.
     * @param localName
     *            The local name.
     * @return The attribute's type, or null if there is no matching attribute.
     * @see org.xml.sax.Attributes#getType(java.lang.String,java.lang.String)
     */
    @Override
    public String getType(final String uri, final String localName) {
        throw new UnsupportedOperationException();
    }

    /**
     * Look up an attribute's type by qualified (prefixed) name.
     *
     * @param qName
     *            The qualified name.
     * @return The attribute's type, or null if there is no matching attribute.
     * @see org.xml.sax.Attributes#getType(java.lang.String)
     */
    @Override
    public String getType(final String qName) {
        throw new UnsupportedOperationException();
    }

    /**
     * Look up an attribute's value by Namespace-qualified name.
     *
     * @param uri
     *            The Namespace URI, or the empty string for a name with no
     *            explicit Namespace URI.
     * @param localName
     *            The local name.
     * @return The attribute's value, or null if there is attsno matching
     *         attribute.
     * @see org.xml.sax.Attributes#getValue(java.lang.String,java.lang.String)
     */
    @Override
    public String getValue(final String uri, final String localName) {
        return value[getIndex(uri, localName)];
    }

    /**
     * Look up an attribute's value by qualified (prefixed) name.
     *
     * @param qName
     *            The qualified name.
     * @return The attribute's value, or null if there is no matching attribute.
     * @see org.xml.sax.Attributes#getValue(java.lang.String)
     */
    @Override
    public String getValue(final String qName) {
        return value[getIndex(qName)];
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(getURI(i));
            sb.append(COMMA);
            sb.append(getLocalName(i));
            sb.append(COMMA);
            sb.append(getQName(i));
            sb.append(COMMA);
            sb.append(getValue(i));
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof NPAttributes)) {
            return false;
        }

        final NPAttributes atts = (NPAttributes) obj;

        if (length != atts.length) {
            return false;
        }
        if ((namePool != null && atts.namePool == null) || (namePool == null && atts.namePool != null)) {
            return false;
        } else if (namePool != null && atts.namePool != null && !namePool.equals(atts.namePool)) {
            return false;
        }
        if ((nameCode != null && atts.nameCode == null) || (nameCode == null && atts.nameCode != null)) {
            return false;
        } else if (nameCode != null && atts.nameCode != null) {
            if (nameCode.length != atts.nameCode.length) {
                return false;
            }
            for (int i = 0; i < nameCode.length; i++) {
                if (nameCode[i] != atts.nameCode[i]) {
                    return false;
                }
            }
        }
        if ((value != null && atts.value == null) || (value == null && atts.value != null)) {
            return false;
        } else if (value != null && atts.value != null) {
            if (value.length != atts.value.length) {
                return false;
            }
            for (int i = 0; i < value.length; i++) {
                if ((value[i] != null && atts.value[i] == null) || (value[i] == null && atts.value[i] != null)) {
                    return false;
                }
                if (value[i] != null && atts.value[i] != null) {
                    if (!value[i].equals(atts.value[i])) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
