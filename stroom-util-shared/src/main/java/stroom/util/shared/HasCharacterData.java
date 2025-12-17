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

package stroom.util.shared;

import java.util.Optional;

public interface HasCharacterData {

    boolean areNavigationControlsVisible();

    NavigationMode getNavigationMode();

    DataRange getDataRange();

    /**
     * @return The total number of lines if known
     */
    default Optional<Long> getTotalLines() {
        return Optional.ofNullable(getDataRange())
                .filter(dataRange -> dataRange.getOptLocationFrom().isPresent()
                        && dataRange.getOptLocationTo().isPresent())
                .map(dataRange -> dataRange.getLocationTo().getLineNo()
                        - dataRange.getLocationFrom().getLineNo()
                        + 1L); // line nos are inclusive, so add 1
    }

    default Optional<Integer> getLineFrom() {
        return Optional.ofNullable(getDataRange())
                .flatMap(DataRange::getOptLocationFrom)
                .map(Location::getLineNo);
    }

    default Optional<Integer> getLineTo() {
        return Optional.ofNullable(getDataRange())
                .flatMap(DataRange::getOptLocationTo)
                .map(Location::getLineNo);
    }

    /**
     * The char offset to display from
     * Zero based, inclusive
     */
    default Optional<Long> getCharOffsetFrom() {
        return Optional.ofNullable(getDataRange())
                .flatMap(DataRange::getOptCharOffsetFrom);
    }

    /**
     * The char offset to display to
     * Zero based, inclusive
     */
    default Optional<Long> getCharOffsetTo() {
        return Optional.ofNullable(getDataRange())
                .flatMap(DataRange::getOptCharOffsetTo);
    }

    /**
     * The byte offset to display from
     * Zero based, inclusive
     */
    default Optional<Long> getByteOffsetFrom() {
        return Optional.ofNullable(getDataRange())
                .flatMap(DataRange::getOptByteOffsetFrom);
    }

    /**
     * The byte offset to display to
     * Zero based, inclusive
     */
    default Optional<Long> getByteOffsetTo() {
        return Optional.ofNullable(getDataRange())
                .flatMap(DataRange::getOptByteOffsetTo);
    }

    boolean isSegmented();

    /**
     * @return The total number of chars in the source, if known
     */
    Count<Long> getTotalChars();

    /**
     * @return The total number of bytes in the source, if known
     */
    Optional<Long> getTotalBytes();

    /**
     * Called when the user clicks the |<< button. I.e. Show the head
     * of the file.
     */
    void showHeadCharacters();

    /**
     * Called when the user clicks the >| button
     */
    void advanceCharactersForward();

    /**
     * Called when the user clicks the |< button
     */
    void advanceCharactersBackwards();

    /**
     * Called when the user clicks refresh
     */
    void refresh();

    /**
     * Called when the character range popup is closed with a new range
     *
     * @param dataRange The new data range
     */
    void setDataRange(final DataRange dataRange);

    enum NavigationMode {
        CHARS,
        BYTES;
    }
}
