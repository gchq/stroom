package stroom.security.shared;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentPermissions {

    @Test
    void excludePermissions1() {
        final Map<String, Set<String>> perms = Map.of(
                "user1", Set.of("A", "B", "C"));

        final Map<String, Set<String>> permsToExclude = Map.of(
                "user1", Set.of("B"));

        final Map<String, Set<String>> result = DocumentPermissions.excludePermissions(perms, permsToExclude);
        assertThat(result)
                .isEqualTo(
                        Map.of(
                                "user1", Set.of("A", "C")));
    }

    @Test
    void excludePermissions2() {
        final Map<String, Set<String>> perms = Map.of(
                "user1", Set.of("A", "B", "C"), // One item excluded
                "user2", Set.of("A", "B", "C", "D"), // Two items excluded
                "user4", Set.of("D", "E"), // User not in exclude map
                "user5", Set.of("F", "G")); // Excluded completely

        final Map<String, Set<String>> permsToExclude = Map.of(
                "user1", Set.of("B"),
                "user2", Set.of("B", "C"),
                "user3", Set.of("A"), // Not in perms map
                "user5", Set.of("F", "G"));

        final Map<String, Set<String>> result = DocumentPermissions.excludePermissions(perms, permsToExclude);
        assertThat(result)
                .isEqualTo(
                        Map.of(
                                "user1", Set.of("A", "C"),
                                "user2", Set.of("A", "D"),
                                "user4", Set.of("D", "E")));
    }

    @Test
    void excludePermissions_null() {
        final Map<String, Set<String>> perms = Map.of(
                "user1", Set.of("A", "B", "C"),
                "user2", Set.of("A", "B", "C", "D"));
        final Map<String, Set<String>> permsToExclude = null;
        final Map<String, Set<String>> result = DocumentPermissions.excludePermissions(perms, permsToExclude);
        assertThat(result)
                .isEqualTo(perms);
    }

    @Test
    void excludePermissions_empty() {
        final Map<String, Set<String>> perms = Map.of(
                "user1", Set.of("A", "B", "C"),
                "user2", Set.of("A", "B", "C", "D"));
        final Map<String, Set<String>> permsToExclude = Collections.emptyMap();
        final Map<String, Set<String>> result = DocumentPermissions.excludePermissions(perms, permsToExclude);
        assertThat(result)
                .isEqualTo(perms);
    }
}
