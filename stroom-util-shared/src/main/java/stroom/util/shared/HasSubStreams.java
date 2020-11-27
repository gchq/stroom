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
