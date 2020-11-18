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
    Optional<Long> getTotalChars();

    /**
     * @return The total number of bytes in the source, if known
     */
    Optional<Long> getTotalBytes();

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
