package stroom.meta.shared;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

class TestSimpleMetaImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSimpleMetaImpl.class);

    private static final List<Function<SimpleMeta, Object>> FUNCTIONS = List.of(
            SimpleMeta::getId,
            SimpleMeta::getFeedName,
            SimpleMeta::getTypeName,
            SimpleMeta::getCreateMs,
            SimpleMeta::getStatusMs);

    @Test
    void from() {

        final Meta meta = Meta.builder()
                .id(123L)
                .typeName("MY TYPE")
                .feedName("MY FEED")
                .createMs(456L)
                .statusMs(789L)
                .build();

        final SimpleMeta simpleMeta = SimpleMetaImpl.from(meta);

        for (final Function<SimpleMeta, Object> func : FUNCTIONS) {
            final Object metaValue = func.apply(meta);
            final Object simpleMetaValue = func.apply(simpleMeta);

            LOGGER.debug("Comparing {} to {}", simpleMetaValue, metaValue);
            Assertions.assertThat(simpleMetaValue)
                    .isEqualTo(metaValue);
        }
    }

    @Test
    void testNew() {

        final Meta meta = Meta.builder()
                .id(123L)
                .typeName("MY TYPE")
                .feedName("MY FEED")
                .createMs(456L)
                .statusMs(789L)
                .build();

        final SimpleMeta simpleMeta = new SimpleMetaImpl(
                meta.getId(),
                meta.getTypeName(),
                meta.getFeedName(),
                meta.getCreateMs(),
                meta.getStatusMs());

        for (final Function<SimpleMeta, Object> func : FUNCTIONS) {
            final Object metaValue = func.apply(meta);
            final Object simpleMetaValue = func.apply(simpleMeta);

            LOGGER.debug("Comparing {} to {}", simpleMetaValue, metaValue);
            Assertions.assertThat(simpleMetaValue)
                    .isEqualTo(metaValue);
        }
    }

    @Test
    void equals_true() {
        final SimpleMeta simpleMeta1 = SimpleMetaImpl.from(Meta.builder()
                .id(123L)
                .typeName("MY TYPE1")
                .feedName("MY FEED1")
                .createMs(1_456L)
                .statusMs(1_789L)
                .build());

        final SimpleMeta simpleMeta2 = SimpleMetaImpl.from(Meta.builder()
                .id(123L)
                .typeName("MY TYPE2")
                .feedName("MY FEED2")
                .createMs(2_456L)
                .statusMs(2_789L)
                .build());

        Assertions.assertThat(simpleMeta1)
                .isEqualTo(simpleMeta2);
    }

    @Test
    void equals_false() {
        final SimpleMeta simpleMeta1 = SimpleMetaImpl.from(Meta.builder()
                .id(1_123L)
                .typeName("MY TYPE1")
                .feedName("MY FEED1")
                .createMs(1_456L)
                .statusMs(1_789L)
                .build());

        final SimpleMeta simpleMeta2 = SimpleMetaImpl.from(Meta.builder()
                .id(2_123L)
                .typeName("MY TYPE2")
                .feedName("MY FEED2")
                .createMs(2_456L)
                .statusMs(2_789L)
                .build());

        Assertions.assertThat(simpleMeta1)
                .isNotEqualTo(simpleMeta2);
    }
}
