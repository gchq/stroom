package stroom.docstore.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.RWLockFactory;
import stroom.util.string.PatternUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public interface Persistence {

    boolean exists(DocRef docRef);

    void delete(DocRef docRef);

    Map<String, byte[]> read(DocRef docRef) throws IOException;

    void write(DocRef docRef, boolean update, Map<String, byte[]> data) throws IOException;

    List<DocRef> list(String type);

    /**
     * Find docRefs by name and type. Name can be optionally wild carded using '*' to match 0-many chars.
     */
    default List<DocRef> find(final String type,
                              final String nameFilter,
                              final boolean allowWildCards) {
        // Default impl that does all filtering in java. Not efficient for DB impls.
        if (nameFilter == null) {
            return Collections.emptyList();
        } else {

            final Predicate<DocRef> predicate;
            if (allowWildCards && PatternUtil.containsWildCards(nameFilter)) {
                final Pattern pattern = PatternUtil.createPatternFromWildCardFilter(nameFilter);
                predicate = docRef ->
                        pattern.matcher(docRef.getName()).matches();
            } else {
                predicate = docRef ->
                        nameFilter.equals(docRef.getName());
            }
            return list(type)
                    .stream()
                    .filter(predicate)
                    .collect(Collectors.toList());
        }
    }

    RWLockFactory getLockFactory();
}
