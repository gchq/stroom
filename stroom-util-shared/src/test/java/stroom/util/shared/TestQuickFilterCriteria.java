package stroom.util.shared;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reason {@link QuickFilterCriteria} exists rather than each criteria carrying its own field:
 * five hand-written copy constructors silently dropped the filter, so a request rebuilt server-side
 * came back unfiltered. Anything extending this gets it right by construction, and this pins that.
 */
class TestQuickFilterCriteria {

    @Test
    void copyBuiltCriteriaKeepsItsFilter() {
        final TestCriteria original = new TestCriteria.Builder()
                .quickFilter("some text")
                .pageRequest(PageRequest.unlimited())
                .build();

        // The shape the permission services use: rebuild to override one field, keep the rest.
        final TestCriteria rebuilt = new TestCriteria.Builder(original)
                .pageRequest(new PageRequest(0, 10))
                .build();

        assertThat(rebuilt.getQuickFilter()).isEqualTo("some text");
        assertThat(rebuilt.getPageRequest().getLength()).isEqualTo(10);
    }

    @Test
    void copyBuiltCriteriaWithNoFilterStaysNull() {
        final TestCriteria original = new TestCriteria.Builder().build();
        assertThat(new TestCriteria.Builder(original).build().getQuickFilter()).isNull();
    }

    @Test
    void theFilterCanBeOverriddenOnACopy() {
        final TestCriteria original = new TestCriteria.Builder().quickFilter("old").build();
        assertThat(new TestCriteria.Builder(original).quickFilter("new").build().getQuickFilter())
                .isEqualTo("new");
    }


    // --------------------------------------------------------------------------------


    private static class TestCriteria extends QuickFilterCriteria {

        TestCriteria(final PageRequest pageRequest,
                     final List<CriteriaFieldSort> sortList,
                     final String quickFilter) {
            super(pageRequest, sortList, quickFilter);
        }

        private static class Builder extends QuickFilterCriteriaBuilder<TestCriteria, Builder> {

            Builder() {
            }

            Builder(final TestCriteria criteria) {
                super(criteria);
            }

            @Override
            protected Builder self() {
                return this;
            }

            @Override
            public TestCriteria build() {
                return new TestCriteria(pageRequest, sortList, quickFilter);
            }
        }
    }
}
