package stroom.util.shared;

import java.util.Optional;

public interface HasCharacterData {

    boolean isMultiPart();
    /**
     * zero based
     */
    Optional<Long> getPartNo();

    Optional<Long> getTotalParts();

    void setPartNo(final long partNo);


    boolean isSegmented();

    boolean canDisplayMultipleSegments();
    /**
     * zero based, inclusive
     */
    Optional<Long> getSegmentNoFrom();

    /**
     * zero based, inclusive
     */
    Optional<Long> getSegmentNoTo();

    Optional<Long> getTotalSegments();

    Optional<String> getSegmentName();

    void setSegmentNoFrom(final long partNo);

    Optional<Long> getTotalLines();

    /**
     * One based, inclusive
     */
    Optional<Long> getCharFrom();

    /**
     * One based, inclusive
     */
    Optional<Long> getCharTo();

    Optional<Long> getTotalChars();



    void showHeadCharacters();

    void advanceCharactersForward();

    void advanceCharactersBackwards();

    void refresh();

}
