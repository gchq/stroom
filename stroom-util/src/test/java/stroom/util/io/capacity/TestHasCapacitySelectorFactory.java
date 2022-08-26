package stroom.util.io.capacity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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
}
