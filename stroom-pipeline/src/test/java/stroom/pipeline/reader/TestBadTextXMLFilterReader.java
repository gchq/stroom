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


import org.junit.jupiter.api.Test;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class TestBadTextXMLFilterReader {

    //  Used to replace an unknown, unrecognized, or unrepresentable character
    @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
    private static final char REPLACEMENT_CHARACTER = '\ufffd'; // REPLACEMENT CHARACTER
    //  High Surrogates
    @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
    private static final char HIGH_SURROGATE = '\uD801'; // INVALID CHARACTER
    //  Low Surrogates
    @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
    private static final char LOW_SURROGATE = '\uDC01'; // INVALID CHARACTER

    private static final String m_aTrivialValidXML = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<TopLevelEntity>" +
            " <Level1>First level one text</Level1>" +
            " <Level1>Second level one text</Level1>" +
            "</TopLevelEntity>");
    private static final String m_aTrivialInValidXML = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<TopLevelEntity>" +
            "<Leaf>The leaf contains > and < and & characters</Leaf>" +
            "<Leaf>bogus <enter> and </exit> tags</Leaf>" +
            "</TopLevelEntity>");

    private static final String m_aTrivialInValidXMLcorrected = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<TopLevelEntity>" +
            "<Leaf>The leaf contains &gt; and &lt; and &amp; characters</Leaf>" +
            "<Leaf>bogus &lt;enter&gt; and &lt;/exit&gt; tags</Leaf>" +
            "</TopLevelEntity>");
    private static final String m_aOddValidXML = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<TopLevelEntity  argA=\"String\" argB='String'>" +
            "<Leaf argC=\"with \"\" characters\">Leaf Text" +
            "</Leaf>" +
            "< InvalidTag>< /InvalidTag>" +
            "<Leaf >Test with space</Leaf>" +
            "<Leaf>Test with spaces</Leaf   >" +
            "<text>Unicode " +
            REPLACEMENT_CHARACTER +
            " and surrogatepair " +
            HIGH_SURROGATE +
            LOW_SURROGATE +
            " </text>" +
            "<emptyEntity/>" +
            "</TopLevelEntity >");

    private Reader getReader(final char[] data) {
        return new BadTextXMLFilterReader(new CharArrayReader(data), new String[]{"Leaf"});
    }

    private void checkByChar(final String in, final String correct) throws IOException {
        final CharBuffer res = CharBuffer.allocate(1024 * 1024);
        final Reader filtered = getReader(in.toCharArray());
        while (true) {
            final int ch = filtered.read();
            if (ch == -1) {
                break;
            }
            res.put((char) ch);
        }
        final String filteredVal = String.copyValueOf(res.array(), 0, res.position());
        assertThat(correct).isEqualTo(filteredVal);
    }

    private void checkByArray(final String in, final String correct) throws IOException {
        final char[] correctChars = correct.toCharArray();
        final int[] chunkSizes = {1, 2, 3, 5, 7, 11, 13, 16};
        for (final int chunkSize : chunkSizes) {
            final Reader filtered = getReader(in.toCharArray());
            final char[] res = new char[chunkSize];
            final int inLen = correctChars.length;
            final int trail_size = inLen % chunkSize;
            final int num_chunks = inLen / chunkSize;
            for (int chunk = 0; chunk < num_chunks; chunk++) {
                final int rret = filtered.read(res);
                assertThat(chunkSize).isEqualTo(rret);
                for (int ch = 0; ch != rret; ch++) {
                    assertThat(correctChars[chunk * chunkSize + ch]).isEqualTo(res[ch]);
                }
            }
            if (trail_size > 0) {
                final int rret = filtered.read(res);
                assertThat(trail_size).isEqualTo(rret);
                for (int ch = 0; ch != rret; ch++) {
                    assertThat(correctChars[num_chunks * chunkSize + ch]).isEqualTo(res[ch]);
                }
            }
            final int rret = filtered.read(res);
            assertThat(-1).isEqualTo(rret);
        }
    }

    @Test
    void testTrivialValidXMLbyChar() throws IOException {
        checkByChar(m_aTrivialValidXML, m_aTrivialValidXML);
    }

    @Test
    void testTrivialValidXMLbyArray() throws IOException {
        checkByArray(m_aTrivialValidXML, m_aTrivialValidXML);
    }

    @Test
    void testTrivialInValidXMLbyChar() throws IOException {
        checkByChar(m_aTrivialInValidXML, m_aTrivialInValidXMLcorrected);
    }

    @Test
    void testTrivialInValidXMLbyArray() throws IOException {
        checkByChar(m_aTrivialInValidXML, m_aTrivialInValidXMLcorrected);
    }

    @Test
    void testOddValidXMLbyChar() throws IOException {
        checkByChar(m_aOddValidXML, m_aOddValidXML);
    }

}
