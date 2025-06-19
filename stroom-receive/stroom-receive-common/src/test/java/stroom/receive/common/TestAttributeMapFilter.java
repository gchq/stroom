package stroom.receive.common;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TestAttributeMapFilter {

    @Test
    void wrap_null() {
        final AttributeMapFilter filter = AttributeMapFilter.wrap((AttributeMapFilter) null);
        assertThat(filter)
                .isInstanceOf(ReceiveAllAttributeMapFilter.class);
    }

    @Test
    void wrap_nullList() {
        final AttributeMapFilter filter = AttributeMapFilter.wrap((List<AttributeMapFilter>) null);
        assertThat(filter)
                .isInstanceOf(ReceiveAllAttributeMapFilter.class);
    }

    @Test
    void wrap_emptyList() {
        final AttributeMapFilter filter = AttributeMapFilter.wrap(Collections.emptyList());
        assertThat(filter)
                .isInstanceOf(ReceiveAllAttributeMapFilter.class);
    }

    @Test
    void wrap_oneFilter() {
        final AtomicBoolean filter1Called = new AtomicBoolean(false);
        final AttributeMapFilter filter1 = attributeMap -> {
            filter1Called.set(true);
            return true;
        };
        final AttributeMapFilter combinedFilter = AttributeMapFilter.wrap(filter1);
        combinedFilter.filter(null);

        assertThat(filter1Called)
                .isTrue();
    }

    @Test
    void wrap_twoFilters_1() {
        final AtomicBoolean filter1Called = new AtomicBoolean(false);
        final AttributeMapFilter filter1 = attributeMap -> {
            filter1Called.set(true);
            return true;
        };
        final AtomicBoolean filter2Called = new AtomicBoolean(false);
        final AttributeMapFilter filter2 = attributeMap -> {
            filter2Called.set(true);
            return false;
        };
        final AttributeMapFilter combinedFilter = AttributeMapFilter.wrap(filter1, filter2);
        final boolean result = combinedFilter.filter(null);

        assertThat(result)
                .isFalse(); // filter2 returned false

        assertThat(filter1Called)
                .isTrue(); // Filter1 returned true
        assertThat(filter2Called)
                .isTrue();
    }

    @Test
    void wrap_twoFilters_2() {
        final AtomicBoolean filter1Called = new AtomicBoolean(false);
        final AttributeMapFilter filter1 = attributeMap -> {
            filter1Called.set(true);
            return false;
        };
        final AtomicBoolean filter2Called = new AtomicBoolean(false);
        final AttributeMapFilter filter2 = attributeMap -> {
            filter2Called.set(true);
            return true;
        };
        final AttributeMapFilter combinedFilter = AttributeMapFilter.wrap(filter1, filter2);
        final boolean result = combinedFilter.filter(null);

        assertThat(result)
                .isFalse(); // filter2 returned false

        assertThat(filter1Called)
                .isTrue(); // Filter1 returned true
        assertThat(filter2Called)
                .isFalse();
    }

    @Test
    void wrap_threeFilters_rejectAll() {
        final AtomicBoolean filter1Called = new AtomicBoolean(false);
        final AttributeMapFilter filter1 = attributeMap -> {
            filter1Called.set(true);
            return true;
        };
        final AtomicBoolean filter2Called = new AtomicBoolean(false);
        final AttributeMapFilter filter2 = attributeMap -> {
            filter2Called.set(true);
            return false;
        };
        final AttributeMapFilter combinedFilter = AttributeMapFilter.wrap(
                filter1,
                filter2,
                RejectAllAttributeMapFilter.getInstance());
        Assertions.assertThatThrownBy(
                        () -> {
                            combinedFilter.filter(null);
                        })
                .isInstanceOf(StroomStreamException.class);

        assertThat(filter1Called)
                .isFalse();
        assertThat(filter2Called)
                .isFalse();
    }
}
