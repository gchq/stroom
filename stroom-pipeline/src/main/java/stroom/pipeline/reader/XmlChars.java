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

interface XmlChars {

    /**
     * Returns true if the specified character is valid. This method
     * also checks the surrogate character range from 0x10000 to 0x10FFFF.
     *
     * @param c The character to check.
     */
    boolean isValid(int c);

    /**
     * Returns true if the specified character is valid and not a control
     * character. See https://www.w3.org/TR/xml11/#NT-RestrictedChar
     * This method also checks the surrogate character range from 0x10000 to 0x10FFFF.
     *
     * @param c The character to check.
     */
    boolean isValidLiteral(int c);

    String getXmlVersion();
}
