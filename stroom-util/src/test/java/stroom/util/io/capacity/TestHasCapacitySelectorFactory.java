package stroom.util.io.capacity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

class TestHasCapacitySelectorFactory {

    @Test
    void createSelector() {
        final HasCapacitySelectorFactory factory = new HasCapacitySelectorFactory();
        for (final String selectorName : factory.getSelectorNames()) {
            final Optional<HasCapacitySelector> optSelector = factory.createSelector(selectorName);
            Assertions.assertThat(optSelector)
                    .isPresent();
            Assertions.assertThat(optSelector.get().getName())
                    .isEqualTo(selectorName);
        }
    }

    @Test
    void createSelectorOrDefault() {
        final HasCapacitySelectorFactory factory = new HasCapacitySelectorFactory();
        for (final String selectorName : factory.getSelectorNames()) {
            final HasCapacitySelector selector = factory.createSelectorOrDefault(selectorName);
            Assertions.assertThat(selector)
                    .isNotNull();
            Assertions.assertThat(selector.getName())
                    .isEqualTo(selectorName);
        }
    }

    @Test
    void createSelector_unknown() {
        final HasCapacitySelectorFactory factory = new HasCapacitySelectorFactory();
        final Optional<HasCapacitySelector> optSelector = factory.createSelector("DUMMY");
        Assertions.assertThat(optSelector)
                .isEmpty();
    }

    @Test
    void createSelector_null() {
        final HasCapacitySelectorFactory factory = new HasCapacitySelectorFactory();
        final Optional<HasCapacitySelector> optSelector = factory.createSelector(null);
        Assertions.assertThat(optSelector)
                .isEmpty();
    }

    @Test
    void createSelectorOrDefault_unknown() {
        final HasCapacitySelectorFactory factory = new HasCapacitySelectorFactory();
        final HasCapacitySelector selector = factory.createSelectorOrDefault("DUMMY");
        Assertions.assertThat(selector)
                .isNotNull();
        Assertions.assertThat(selector.getName())
                .isEqualTo(HasCapacitySelectorFactory.DEFAULT_SELECTOR_NAME);
    }

    @Test
    void createSelectorOrDefault_null() {
        final HasCapacitySelectorFactory factory = new HasCapacitySelectorFactory();
        final HasCapacitySelector selector = factory.createSelectorOrDefault(null);
        Assertions.assertThat(selector)
                .isNotNull();
        Assertions.assertThat(selector.getName())
                .isEqualTo(HasCapacitySelectorFactory.DEFAULT_SELECTOR_NAME);
    }

    /**
     * Make sure the pattern for validating the selector names matches the names of the selectors.
     * This is because we can't build the validation pattern dynamically so this protects against a
     * dev changing one without the other.
     */
    @Test
    void testSelectorPattern() {

        String pattern = HasCapacitySelectorFactory.VOLUME_SELECTOR_PATTERN;
        pattern = pattern.replaceAll("^\\^\\(", "")
                .replaceAll("\\)\\$$", "");
        final Set<String> selectorNamesInPattern = new HashSet<>(Arrays.asList(pattern.split("\\|")));

        final Set<String> selectorNames = HasCapacitySelectorFactory.getSelectorNames();

        Assertions.assertThat(selectorNamesInPattern)
                .containsExactlyInAnyOrderElementsOf(selectorNames);
    }
}
