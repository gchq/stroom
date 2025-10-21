package stroom.query.common.v2;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.query.api.IncludeExcludeFilter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestCompiledIncludeExcludeFilter {

    private static final DocRef DICTIONARY1 = new DocRef("Dictionary", UUID.randomUUID().toString(), "dictionary1");
    private static final DocRef DICTIONARY2 = new DocRef("Dictionary", UUID.randomUUID().toString(), "dictionary2");

    @Mock
    WordListProvider wordListProvider;

    @BeforeEach
    public void setup() {
        Mockito.lenient().when(wordListProvider.getWords(DICTIONARY1)).thenReturn(new String[]{"user1"});
        Mockito.lenient().when(wordListProvider.getWords(DICTIONARY2)).thenReturn(new String[]{"user2"});
    }

    private static Stream<Arguments> includeExcludeFilter() {
        return Stream.of(
                Arguments.of(IncludeExcludeFilter.builder().includes("user1\nuser2"),
                        List.of("user1", "user2"), List.of("user3")),
                Arguments.of(IncludeExcludeFilter.builder().includes("user1\n"),
                        List.of("user1", "user10", "user109"), List.of("user2", "user3")),
                Arguments.of(IncludeExcludeFilter.builder().includeDictionaries(List.of(DICTIONARY1, DICTIONARY2)),
                        List.of("user1", "user2"), List.of("user3")),
                Arguments.of(IncludeExcludeFilter.builder().excludes("user1\nuser2"),
                        List.of("user3"), List.of("user1", "user2")),
                Arguments.of(IncludeExcludeFilter.builder().excludeDictionaries(List.of(DICTIONARY1, DICTIONARY2)),
                        List.of("user3"), List.of("user1", "user2")),
                Arguments.of(IncludeExcludeFilter.builder().includes("user3\nuser4")
                                .includeDictionaries(List.of(DICTIONARY1, DICTIONARY2)),
                        List.of("user1", "user2", "user3", "user4"), List.of("user5")),
                Arguments.of(IncludeExcludeFilter.builder().excludes("user3\nuser4")
                                .excludeDictionaries(List.of(DICTIONARY1, DICTIONARY2)),
                        List.of("user5"), List.of("user1", "user2", "user3", "user4")),
                Arguments.of(IncludeExcludeFilter.builder().includes("user")
                                .excludeDictionaries(List.of(DICTIONARY1, DICTIONARY2)),
                        List.of("user3"), List.of("user1", "user2")),
                Arguments.of(IncludeExcludeFilter.builder().includes("user")
                                .excludes("user3\nuser4"),
                        List.of("user1"), List.of("user3", "user4", "bob"))
        );
    }

    @ParameterizedTest
    @MethodSource("includeExcludeFilter")
    void create(final IncludeExcludeFilter.Builder filterBuilder, final List<String> includedStrings,
                final List<String> excludedStrings) {
        final IncludeExcludeFilter filter = filterBuilder.build();

        final Optional<Predicate<String>> optional =
                CompiledIncludeExcludeFilter.create(filter, Map.of(), wordListProvider);

        assertThat(optional.isPresent()).isTrue();

        final Predicate<String> predicate = optional.get();

        includedStrings.forEach(
                s -> assertThat(predicate.test(s)).as("\"" + s + "\" is false").isTrue());
        excludedStrings.forEach(
                s -> assertThat(predicate.test(s)).as("\"" + s + "\" is true").isFalse());
    }
}
