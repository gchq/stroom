package stroom.util.shared;

import java.util.Optional;

public interface HasCharacterData {

    boolean areNavigationControlsVisible();

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
     *
     * @param dataRange The new data range
     */
    void setDataRange(final DataRange dataRange);
}
