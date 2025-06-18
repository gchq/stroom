package stroom.proxy.repo;

import stroom.proxy.repo.FeedKey.FeedKeyInterner;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedKey {

    @Test
    void testIntern_null() {
        final FeedKeyInterner interner = FeedKey.createInterner();

        assertThat(interner.intern(null))
                .isNull();
    }

    @Test
    void testIntern_1() {
        final FeedKeyInterner interner = FeedKey.createInterner();

        final FeedKey feedKey1a = new FeedKey("feed1", "type1");
        final FeedKey feedKey1b = new FeedKey("feed1", "type1");
        final FeedKey feedKey2 = new FeedKey("feed2", "type1");
        final FeedKey feedKey3 = new FeedKey("feed1", "type2");
        final FeedKey feedKey4 = new FeedKey("feed2", "type2");

        assertThat(feedKey1a)
                .isEqualTo(feedKey1b)
                .isNotEqualTo(feedKey2)
                .isNotEqualTo(feedKey3)
                .isNotEqualTo(feedKey4);

        assertThat(feedKey1a.hashCode())
                .isEqualTo(feedKey1a.hashCode())
                .isEqualTo(feedKey1b.hashCode())
                .isNotEqualTo(feedKey2.hashCode())
                .isNotEqualTo(feedKey3.hashCode())
                .isNotEqualTo(feedKey4.hashCode());

        assertThat(feedKey2)
                .isNotEqualTo(feedKey3)
                .isNotEqualTo(feedKey4);

        assertThat(feedKey3)
                .isNotEqualTo(feedKey4);

        assertThat(interner.intern(feedKey1a))
                .isEqualTo(feedKey1a)
                .isSameAs(feedKey1a);
        assertThat(interner.intern(feedKey1b))
                .isEqualTo(feedKey1a)
                .isEqualTo(feedKey1b)
                .isSameAs(feedKey1a);

        assertThat(interner.intern("feed1", "type1"))
                .isEqualTo(feedKey1a)
                .isEqualTo(feedKey1b)
                .isSameAs(feedKey1a);
    }
}
