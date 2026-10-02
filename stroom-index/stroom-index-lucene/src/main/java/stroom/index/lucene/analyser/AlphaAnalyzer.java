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

package stroom.index.lucene.analyser;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;

class AlphaAnalyzer extends Analyzer {

    private final boolean caseSensitive;

    AlphaAnalyzer(final boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    @Override
    protected TokenStreamComponents createComponents(final String fieldName) {
        final AlphaTokenizer src = new AlphaTokenizer();
        return new TokenStreamComponents(src, normalize(fieldName, src));
    }

    @Override
    protected TokenStream normalize(final String fieldName, final TokenStream in) {
        if (caseSensitive) {
            return in;
        }
        return new LowerCaseFilter(in);
    }
}
