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

package stroom.index.lucene.analyser;

import stroom.test.common.util.test.StroomUnitTest;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;

public class TestStandardAnalyser extends StroomUnitTest {

    public static void main(final String[] args) throws IOException {
        new TestStandardAnalyser().test();
    }

    public void test() throws IOException {
        String in = "SOME-HYPHENATED-STRING";

        testAnalyser(in, new StandardAnalyzer());
        testAnalyser(in, new SimpleAnalyzer());
        testAnalyser(in, new EnglishAnalyzer());

        in = "user1";

        testAnalyser(in, new StandardAnalyzer());
        testAnalyser(in, new SimpleAnalyzer());
        testAnalyser(in, new EnglishAnalyzer());
    }

    private void testAnalyser(final String input, final Analyzer analyzer) throws IOException {
        System.out.println("Testing analyser: " + analyzer.getClass().getName());

        final ReusableStringReader reader = new ReusableStringReader();
        reader.init(input);

        final TokenStream stream = analyzer.tokenStream("Test", reader);

        // reset the TokenStream to the first token
        stream.reset();

        boolean hasMoreTokens = stream.incrementToken();

        final CharTermAttribute termAtt = stream.addAttribute(CharTermAttribute.class);

        for (; ; ) {
            if (!hasMoreTokens) {
                break;
            }

            // Get the text of this term.
            final char[] tokenText = termAtt.buffer();
            final int tokenTextLen = termAtt.length();

            System.out.println(new String(tokenText, 0, tokenTextLen));

            hasMoreTokens = stream.incrementToken();
        }
    }
}
