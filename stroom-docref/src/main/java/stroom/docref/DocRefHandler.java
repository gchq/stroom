package stroom.docref;

public interface DocRefHandler extends HasFindDocsByName {

    /**
     * Retrieve the audit information for a particular doc ref
     *
     * @param uuid The UUID to return the information for
     * @return The Audit information about the given DocRef.
     */
    DocRefInfo info(String uuid);
}
