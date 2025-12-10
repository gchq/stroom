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

package stroom.security.identity.token;

public enum KeyType {
    API("api");

    private final String text;

    KeyType(final String text) {
        this.text = text;
    }

    public String getText() {
        return this.text;
    }

    public static KeyType fromText(final String value) {
        // Not enough values to warrant an EnumMap
        if (value != null) {
            final String caseInsensitiveValue = value.toLowerCase();
            for (final KeyType keyType : KeyType.values()) {
                if (keyType.getText().equals(caseInsensitiveValue)) {
                    return keyType;
                }
            }
        }
        throw new IllegalArgumentException("Unknown API key type " + value);
    }
}
