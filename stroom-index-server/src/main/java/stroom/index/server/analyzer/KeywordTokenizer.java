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

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.Version;

import java.io.Reader;

public class KeywordTokenizer extends CharTokenizer {
    public KeywordTokenizer(final Version matchVersion, final Reader in) {
        super(matchVersion, in);
    }

    @Override
    protected boolean isTokenChar(final int c) {
        return true;
    }

    @Override
    protected int normalize(final int c) {
        return Character.toLowerCase(c);
    }
}
