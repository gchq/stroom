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

package stroom.pipeline.reader;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * An implementation of TransformReader to transform malformed XML documents.
 * <p>
 * Leaf Entities are XML entity names for which, it is assumed, text delimited
 * by the start and end tags should have been escaped to avoid '<'; '>' and '&'
 * characters being interpreted as XML syntax.
 */
@SuppressWarnings("checkstyle:membername")
public class BadTextXMLFilterReader extends TransformReader {

    private static final int MAX_CAPACITY = 1024 * 1024; // 1 million characters
    // should be larger
    // than any single
    // entity.
    private final CharBuffer m_cbuf;
    private final Set<String> m_forceLeafElements;
    private XMLstate m_xmlState;
    private int m_cbufUsed;
    private int m_leafBeginText;
    private int m_leafEndText;
    private int m_cachedCP;

    public BadTextXMLFilterReader(final Reader in, final String[] forceLeafEntities) {
        super(in);
        m_cbuf = CharBuffer.allocate(MAX_CAPACITY);
        m_cbufUsed = 0;
        m_cachedCP = -1;
        m_forceLeafElements = new HashSet<>();
        m_forceLeafElements.addAll(Arrays.asList(forceLeafEntities));
        m_xmlState = XMLstate.Initial;
    }

    private static boolean isNameStartChar(final int cp) {
        return (cp >= 'A' && cp <= 'Z')
                || (cp >= 'a' && cp <= 'z')
                || cp == ':'
                || cp == '_'
                || (cp >= 0xc0 && cp <= 0xd6)
                || (cp >= 0xd8 && cp <= 0xf6)
                || (cp >= 0xf8 && cp <= 0x2ff)
                || (cp >= 0x370 && cp <= 0x37d)
                || (cp >= 0x37f && cp <= 0x1fff)
                || (cp >= 0x200c && cp <= 0x200d)
                || (cp >= 0x2070 && cp <= 0x218f)
                || (cp >= 0x2c00 && cp <= 0x2fef)
                || (cp >= 0x3001 && cp <= 0xD7ff)
                || (cp >= 0xf900 && cp <= 0xfdcf)
                || (cp >= 0xfdf0 && cp <= 0xfffd)
                || (cp >= 0x10000 && cp <= 0xeffff);
    }

    private static boolean isNameChar(final int cp) {
        return isNameStartChar(cp)
                || cp == '-'
                || cp == '.'
                || Character.isDigit(cp)
                || cp == 0x87
                || (cp >= 0x300 && cp <= 0x36f)
                || (cp >= 0x203f && cp <= 0x2040);
    }

    @Override
    public int read() throws IOException {
        if (m_cbufUsed - m_cbuf.position() <= 0) {
            if (!readFromBadXML()) {
                return -1;
            }
        }
        return m_cbuf.get();
    }

    @Override
    public int read(final char[] cbuf, int off, int len) throws IOException {
        final int initialOff = off;
        int cpysize;
        while (len > 0) {
            cpysize = m_cbufUsed - m_cbuf.position();
            if (cpysize <= 0) {
                if (!readFromBadXML()) {
                    break;
                }
                cpysize = m_cbufUsed - m_cbuf.position();
            }
            if (cpysize > len) {
                cpysize = len;
            }
            m_cbuf.get(cbuf, off, cpysize);
            off += cpysize;
            len -= cpysize;
        }
        final int charsRead = off - initialOff;
        return charsRead > 0
                ? charsRead
                : -1;
    }

    private void patchUpLeafNonXML() {
        final String raw = String.copyValueOf(m_cbuf.array(), m_leafBeginText, m_leafEndText - m_leafBeginText);
        final String openEntity = String.copyValueOf(m_cbuf.array(), 0, m_leafBeginText);
        final String closeEntity = String.copyValueOf(m_cbuf.array(), m_leafEndText, m_cbufUsed - m_leafEndText);
        m_cbuf.put(openEntity);
        for (final char ch : raw.toCharArray()) {
            switch (ch) {
                case '&':
                    m_cbuf.put("&amp;");
                    break;
                case '<':
                    m_cbuf.put("&lt;");
                    break;
                case '>':
                    m_cbuf.put("&gt;");
                    break;
                default:
                    m_cbuf.put(ch);
            }
        }
        m_cbuf.put(closeEntity);
        m_cbufUsed = m_cbuf.position();
        m_cbuf.rewind();
    }

    private boolean foundXMLChunk(final boolean consume) {
        if (consume) {
            writeCP(m_cachedCP);
            m_cachedCP = -1;
        }
        m_cbufUsed = m_cbuf.position();
        m_cbuf.rewind();
        switch (m_xmlState) {
            case InLeafClose:
                modified = true;
                patchUpLeafNonXML();
                break;
            case InTagTail:
            case InTagSpecial:
            case InTextValid:
                break;
            default:
                assert (false);
        }
        m_xmlState = XMLstate.Initial;
        return true;
    }

    private void copyQuotedString() throws IOException {
        final char qChar = (char) m_cachedCP;
        for (; ; ) {
            writeCP(m_cachedCP);
            m_cachedCP = readCP();
            if (m_cachedCP == qChar) {
                m_cachedCP = readCP();
                writeCP(qChar);
                if (m_cachedCP != qChar) {
                    break;
                }
            }
        }
    }

    @SuppressWarnings({"checkstyle:missingswitchdefault", "checkstyle:fallthrough", "localvariablename"})
    private boolean readFromBadXML() throws IOException {
        int entityNameidx = -1;
        m_leafBeginText = m_leafEndText = -1;
        String m_entityName = null;
        m_cbuf.rewind();

        if (m_cachedCP == -1) {
            m_cachedCP = readCP();
        }
        while (m_cachedCP != -1) {
            switch (m_xmlState) {
                case Initial:
                    switch (m_cachedCP) {
                        case '<':
                            m_xmlState = XMLstate.InTagPreName;
                            break;
                        default:
                            m_xmlState = XMLstate.InTextValid;
                    }
                    break;
                case InTagPreName:
                    switch (m_cachedCP) {
                        case '/':
                            m_xmlState = XMLstate.InTagCloseName;
                            break;
                        case '?':
                            m_xmlState = XMLstate.InTagSpecial;
                            break;
                        default:
                            if (isNameStartChar(m_cachedCP)) {
                                m_xmlState = XMLstate.InTagName;
                                entityNameidx = m_cbuf.position();
                                break;
                            }
                    }
                    break;
                case InTagCloseName:
                    if (isNameStartChar(m_cachedCP)) {
                        m_xmlState = XMLstate.InTagName;
                        entityNameidx = m_cbuf.position();
                        break;
                    }
                case InTagName:
                    // Just seen '</?[-NameStartChar-]'
                    if (isNameChar(m_cachedCP)) {
                        break;
                    }
                    m_entityName = String.copyValueOf(m_cbuf.array(), entityNameidx, m_cbuf.position() - entityNameidx);
                    m_xmlState = XMLstate.InTagTail;
                    // Intended to fall through...
                case InTagTail:
                    // Intended to fall through...
                case InTagSpecial:
                    switch (m_cachedCP) {
                        case '"':
                        case '\'':
                            copyQuotedString();
                            if (m_cachedCP != '>') {
                                break;
                            }
                            // Intended to fall through...
                        case '>':
                            if (m_forceLeafElements.contains(m_entityName)) {
                                m_xmlState = XMLstate.InLeaf;
                                m_leafBeginText = m_cbuf.position() + 1;
                                break;
                            }
                            return foundXMLChunk(true);
                    }
                    break;
                case InTextValid:
                    if (m_cachedCP == '<') {
                        return foundXMLChunk(false);
                    }
                    break;
                case InLeaf:
                    if (m_cachedCP == '<') {
                        m_xmlState = XMLstate.InLeafOpenAngle;
                        m_leafEndText = m_cbuf.position();
                    }
                    break;
                case InLeafOpenAngle:
                    if (m_cachedCP == '/') {
                        m_xmlState = XMLstate.InLeafCheckName;
                        entityNameidx = 0;
                    } else {
                        m_xmlState = XMLstate.InLeaf;
                    }
                    break;
                case InLeafCheckName:
                    if (m_cachedCP == m_entityName.charAt(entityNameidx)) {
                        ++entityNameidx;
                        if (entityNameidx == m_entityName.length()) {
                            m_xmlState = XMLstate.InLeafClose;
                        }
                    } else {
                        m_xmlState = XMLstate.InLeaf;
                    }
                    break;
                case InLeafClose:
                    if (Character.isWhitespace(m_cachedCP)) {
                        break;
                    }
                    if (m_cachedCP == '>') {
                        return foundXMLChunk(true);
                    }
                    break;
                default:
                    assert (false);
            }
            writeCP(m_cachedCP);
            m_cachedCP = readCP();
        }
        m_cbufUsed = 0;
        return false;
    }

    private int readCP() throws IOException {
        int cp = in.read();
        if (Character.isHighSurrogate((char) cp)) {
            final char hiSurrogate = (char) cp;
            cp = in.read();
            assert (Character.isLowSurrogate((char) cp));
            cp = Character.toCodePoint(hiSurrogate, (char) cp);
        }
        return cp;
    }

    private void writeCP(final int cp) {
        assert (cp >= 0 && cp <= 0x10ffff);
        m_cbuf.put(Character.toChars(cp));
    }


    // --------------------------------------------------------------------------------


    private enum XMLstate {
        Initial,
        InTagPreName,
        InTagCloseName,
        InTagName,
        InTagSpecial,
        InTagTail,
        InTextValid,
        InTextInvalid,
        InLeaf,
        InLeafOpenAngle,
        InLeafCheckName,
        InLeafClose
    }

}
