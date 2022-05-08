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

package stroom.pipeline.reader;

import event.logging.Hash;

import java.io.FilterReader;
import java.io.Reader;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An abstract class to implement a FilterReader extended with a
 * hasModifiedContent() method.
 */
public abstract class TransformReader extends FilterReader {
    protected long replacementCount;
    protected Map<Character, Long> replacedCharacters;

    protected TransformReader(final Reader in) {
        super(in);
        replacementCount = 0L;
        replacedCharacters = new HashMap<>();
    }

    /**
     * Has stream content been modified?
     *
     * @return True if, and only if stream contents were transformed.
     */
    public boolean hasModifiedContent() {
        return replacementCount > 0;
    }

    public String getReplacedCharactersAsString() {
        return String.join(", ", replacedCharacters.entrySet().stream()
                .map(entry -> "0x" + Integer.toHexString(entry.getKey()) + ": " + entry.getValue())
                .toArray(String[]::new));
    }

    public long getReplacementCount() {
        return this.replacementCount;
    }

    public Map<Character, Long> getReplacedCharacters() {
        return this.replacedCharacters;
    }

    public void addReplacedCharacter(final char ch) {
        if (!replacedCharacters.containsKey(ch)) {
            replacedCharacters.put(ch, 1L);
        } else {
            replacedCharacters.replace(ch, replacedCharacters.get(ch) + 1);
        }
    }
}
