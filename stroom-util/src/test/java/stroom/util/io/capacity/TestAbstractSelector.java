package stroom.util.io.capacity;

import stroom.util.shared.HasCapacity;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

class TestAbstractSelector extends AbstractHasCapacitySelectorTest {

    HasCapacitySelector selector;

    @Override
    public HasCapacitySelector getSelector() {
        return selector;
    }

    @Test
    void testAllFilteredOut() {
        final FirstItemSelector firstItemSelector = new FirstItemSelector(null);
        selector = new FilterOutAllSelector(firstItemSelector);

        // All paths get filtered
        testMultipleTimes(PATH_1);

        Assertions.assertThat(firstItemSelector.wasCalled())
                .isTrue();
    }

    @Test
    void testFilterLeavesOne() {
        final FirstItemSelector firstItemFallbackSelector = new FirstItemSelector(null);
        final FirstItemSelector firstItemPrimarySelector = new FirstItemSelector(firstItemFallbackSelector);
        selector = firstItemPrimarySelector;

        // Primary selector always filters to leave the fist item so that is always returned
        testMultipleTimes(PATH_1);

        Assertions.assertThat(firstItemPrimarySelector.wasCalled())
                .isTrue();
        // fallback not needed
        Assertions.assertThat(firstItemFallbackSelector.wasCalled())
                .isFalse();
    }

    private static class FilterOutAllSelector extends AbstractSelector {

        public FilterOutAllSelector(final FirstItemSelector firstItemSelector) {
            super(firstItemSelector);
        }

        @Override
        public String getName() {
            return "Filter out all";
        }

        @Override
        <T extends HasCapacity> T doSelect(final List<T> filteredList) {
            // Don't do any special selection
            return null;
        }

        @Override
        <T extends HasCapacity> List<T> createFilteredList(final List<T> list) {
            // All filtered out
            return Collections.emptyList();
        }
    }

    private class FirstItemSelector extends AbstractSelector {

        private final AtomicBoolean fallbackSelectorCalled = new AtomicBoolean(false);

        public FirstItemSelector(final HasCapacitySelector fallbackSelector) {
            super(fallbackSelector);
        }

        @Override
        public String getName() {
            return "First Item";
        }

        @Override
        <T extends HasCapacity> T doSelect(final List<T> filteredList) {
            // Don't do any special selection
            fallbackSelectorCalled.set(true);
            return filteredList.get(0);
        }

        @Override
        <T extends HasCapacity> List<T> createFilteredList(final List<T> list) {
            return list;
        }

        private boolean wasCalled() {
            return fallbackSelectorCalled.get();
        }
    }
}
