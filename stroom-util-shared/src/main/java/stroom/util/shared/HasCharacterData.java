package stroom.util.shared;

import java.util.Optional;

public interface HasCharacterData {

    boolean areNavigationControlsVisible();

    // Methods for parts

//    boolean isMultiPart();
//
//    /**
//     * The current partNo
//     * zero based
//     */
//    Optional<Long> getPartNo();
//
//    /**
//     * @return The total number of parts in the data, if known
//     */
//    Optional<Long> getTotalParts();
//
//    /**
//     * Called when the user clicks one of the part nav controls
//     * @param partNo The new part number
//     */
//    void setPartNo(final long partNo);
//
//    // Methods for segments
//
//    /**
//     * @return True if the data is segmented.
//     */
//    boolean isSegmented();
//
//    /**
//     * @return True if multiple segments can be displayed at once
//     */
//    boolean canDisplayMultipleSegments();
//    /**
//     * zero based, inclusive
//     */
//    Optional<Long> getSegmentNoFrom();
//
//    /**
//     * zero based, inclusive
//     */
//    Optional<Long> getSegmentNoTo();
//
//    /**
//     * @return The total number of segments in the data, if known
//     */
//    Optional<Long> getTotalSegments();
//
//    Optional<String> getSegmentName();
//
//    /**
//     * Called when the user clicks one of the segment nav controls
//     * @param segmentNoFrom The new segment number
//     */
//    void setSegmentNoFrom(final long segmentNoFrom);
//
//    // Methods for character data
//
//    boolean canNavigateCharacterData();

    /**
     * @return The total number of lines if known
     */
    Optional<Long> getTotalLines();

    DataRange getDataRange();

    Optional<Integer> getLineFrom();

    Optional<Integer> getLineTo();

    /**
     * The char offset to display from
     * Zero based, inclusive
     */
    Optional<Long> getCharFrom();

    /**
     * The char offset to display to
     * Zero based, inclusive
     */
    Optional<Long> getCharTo();

    /**
     * @return The total number of chars in the source, if known
     */
    Optional<Long> getTotalChars();

    /**
     * Called when the user clicks the |< button
     */
    void showHeadCharacters();

    /**
     * Called when the user clicks the > button
     */
    void advanceCharactersForward();

    /**
     * Called when the user clicks the < button
     */
    void advanceCharactersBackwards();

    /**
     * Called when the user clicks refresh
     */
    void refresh();

    /**
     * Called when the character range popup is closed with a new range
     * @param dataRange The new data range
     */
    void setDataRange(final DataRange dataRange);
}
