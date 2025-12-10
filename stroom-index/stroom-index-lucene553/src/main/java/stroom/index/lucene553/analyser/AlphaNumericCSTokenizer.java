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

package stroom.index.lucene553.analyser;

import org.apache.lucene553.analysis.util.CharTokenizer;
import org.apache.lucene553.util.AttributeFactory;

class AlphaNumericCSTokenizer extends CharTokenizer {

    AlphaNumericCSTokenizer() {
    }

    AlphaNumericCSTokenizer(final AttributeFactory factory) {
        super(factory);
    }

    @Override
    protected boolean isTokenChar(final int c) {
        return Character.isLetter(c) || Character.isDigit(c);
    }
}
