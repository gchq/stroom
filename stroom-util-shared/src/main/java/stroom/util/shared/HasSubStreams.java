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

public interface HasSubStreams {

    boolean areNavigationControlsVisible();

    // Methods for parts

    boolean isMultiPart();

    /**
     * The current partNo
     * zero based
     */
    Optional<Long> getPartNo();

    /**
     * @return The total number of parts in the data, if known
     */
    Optional<Long> getTotalParts();

    /**
     * Called when the user clicks one of the part nav controls
     *
     * @param partNo The new part number
     */
    void setPartNo(final long partNo);

    // Methods for segments

    /**
     * @return True if the data is segmented.
     */
    boolean isSegmented();

    /**
     * @return True if multiple segments can be displayed at once
     */
    boolean canDisplayMultipleSegments();

    /**
     * zero based, inclusive
     */
    Optional<Long> getSegmentNoFrom();

    /**
     * zero based, inclusive
     */
    Optional<Long> getSegmentNoTo();

    /**
     * @return The total number of segments in the data, if known
     */
    Optional<Long> getTotalSegments();

    Optional<String> getSegmentName();

    /**
     * Called when the user clicks one of the segment nav controls
     *
     * @param segmentNoFrom The new segment number
     */
    void setSegmentNoFrom(final long segmentNoFrom);

    /**
     * Called when the user clicks refresh
     */
    void refresh();

//    /**
//     * Called when the popup is closed with a new source location
//     * @param partNo Zero based
//     * @param segmentNo Zero based
//     */
//    void setSource(final long partNo, final long segmentNo);
}
