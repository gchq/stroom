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

package stroom.index.server.analyzer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.util.Version;

import stroom.query.shared.IndexField.AnalyzerType;

public final class AnalyzerFactory {
    private AnalyzerFactory() {
        // Factory so private constructor.
    }

    public static Analyzer create(final Version matchVersion, final AnalyzerType analyzerType,
            final boolean caseSensitive) {
        switch (analyzerType) {
        case KEYWORD:
            return new KeywordAnalyzer(matchVersion, caseSensitive);
        case ALPHA:
            return new AlphaAnalyzer(matchVersion, caseSensitive);
        case ALPHA_NUMERIC:
            return new AlphaNumericAnalyzer(matchVersion, caseSensitive);
        case NUMERIC:
            return new NumericAnalyzer(matchVersion);
        case WHITESPACE:
            return new WhitespaceAnalyzer(matchVersion);
        case STOP:
            return new StopAnalyzer(matchVersion);
        case STANDARD:
            return new StandardAnalyzer(matchVersion);
        }

        return new KeywordAnalyzer(matchVersion, true);
    }
}
