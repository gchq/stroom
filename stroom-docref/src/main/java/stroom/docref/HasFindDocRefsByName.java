package stroom.docref;

import java.util.List;

public interface HasFindDocRefsByName {

    /**
     * Find by exact case-sensitive match on the name
     */
    default List<DocRef> findByName(String name) {
        return findByName(name, false);
    }

    /**
     * Find by case-sensitive match on the name.
     * If allowWildCards is true '*' can be used to denote a 0-many char wild card.
     */
    List<DocRef> findByName(final String name, final boolean allowWildCards);
}
