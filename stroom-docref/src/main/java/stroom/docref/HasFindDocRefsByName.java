package stroom.docref;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface HasFindDocRefsByName {

    /**
     * @return A list of all known and readable docRefs.
     */
    Set<DocRef> listDocuments();

    /**
     * Find by exact case-sensitive match on the name
     */
    default List<DocRef> findByName(String name) {
        // GWT so no List.of()
        return name != null
                ? findByNames(Arrays.asList(name), false)
                : Collections.emptyList();
    }

    /**
     * Find by case-sensitive match on the name.
     * If allowWildCards is true '*' can be used to denote a 0-many char wild card.
     */
    default List<DocRef> findByName(final String name, final boolean allowWildCards) {
        // GWT so no List.of()
        return name != null
                ? findByNames(Arrays.asList(name), allowWildCards)
                : Collections.emptyList();
    }

    /**
     * Find by case-sensitive match on the names.
     * If allowWildCards is true '*' can be used to denote a 0-many char wild card.
     * Finds all docRefs associated with any of the names, i.e. an OR.
     */
    List<DocRef> findByNames(final List<String> names, final boolean allowWildCards);
}
