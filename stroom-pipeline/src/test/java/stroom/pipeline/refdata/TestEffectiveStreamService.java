package stroom.pipeline.refdata;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.EffectiveMetaSet.Builder;
import stroom.pipeline.shared.data.PipelineReference;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@ExtendWith(MockitoExtension.class)
class TestEffectiveStreamService {


    protected static final Instant LOOKUP_TIME = LocalDateTime.of(2020, 6, 1, 11, 0)
            .toInstant(ZoneOffset.UTC);
    protected static final long LOOKUP_TIME_MS = LOOKUP_TIME.toEpochMilli();
    protected static final LookupIdentifier LOOKUP_IDENTIFIER = new LookupIdentifier("MY_MAP",
            "MY_KEY",
            LOOKUP_TIME_MS);
    protected static final ReferenceDataResult RESULT = new ReferenceDataResult(LOOKUP_IDENTIFIER);
    protected static final String FEED_NAME = "MY_FEED";
    public static final String DUMMY_FEED = "DUMMY_FEED";
    public static final String DUMMY_TYPE = "DummyType";
    @Mock
    private EffectiveStreamCache mockEffectiveStreamCache;

    private PipelineReference pipelineReference;

    @BeforeEach
    void setUp() {
        pipelineReference = new PipelineReference(
                null,
                DocRef.builder()
                        .type(FeedDoc.TYPE)
                        .uuid(FEED_NAME)
                        .name(FEED_NAME)
                        .build(),
                StreamTypeNames.REFERENCE);
    }

    @Test
    void determineEffectiveStream_noStreamsFound() {

        final EffectiveStreamService effectiveStreamService = new EffectiveStreamService(mockEffectiveStreamCache);

        final long lookupTimeMs = LocalDateTime.of(2020, 6, 1, 11, 0)
                .toEpochSecond(ZoneOffset.UTC);
        final LookupIdentifier lookupIdentifier = new LookupIdentifier("MY_MAP", "MY_KEY", lookupTimeMs);
        final ReferenceDataResult result = new ReferenceDataResult(lookupIdentifier);

        Mockito.when(mockEffectiveStreamCache.get(Mockito.any()))
                .thenReturn(EffectiveMetaSet.empty());

        final Optional<EffectiveMeta> optEffectiveStream = effectiveStreamService.determineEffectiveStream(
                pipelineReference,
                lookupTimeMs,
                result);

        Assertions.assertThat(optEffectiveStream)
                .isEmpty();
    }

    @Test
    void determineEffectiveStream_oneStreamFound_sameDay() {

        final EffectiveMeta effectiveStream1 = buildEffectiveMeta(
                1, LOOKUP_TIME.minus(1, ChronoUnit.HOURS));

        doDetermineTest(Set.of(
                        effectiveStream1),
                optEffectiveStream -> Assertions.assertThat(optEffectiveStream)
                        .hasValue(effectiveStream1));
    }

    @Test
    void determineEffectiveStream_oneStreamFound_veryOld() {

        final EffectiveMeta effectiveStream1 = buildEffectiveMeta(
                1, LOOKUP_TIME.minus(100, ChronoUnit.DAYS));

        doDetermineTest(
                Set.of(
                        effectiveStream1),
                optEffectiveStream -> Assertions.assertThat(optEffectiveStream)
                        .hasValue(effectiveStream1));
    }

    @Test
    void determineEffectiveStream_multStrms_allRecent() {

        // This stream is before lookup time so won't be picked
        final EffectiveMeta effectiveStream1 = buildEffectiveMeta(
                1, LOOKUP_TIME.plus(1, ChronoUnit.HOURS));
        // This is latest one for our lookup time
        final EffectiveMeta effectiveStream2 = buildEffectiveMeta(
                2, LOOKUP_TIME.minus(1, ChronoUnit.HOURS));
        final EffectiveMeta effectiveStream3 = buildEffectiveMeta(
                3, LOOKUP_TIME.minus(2, ChronoUnit.HOURS));

        doDetermineTest(
                Set.of(
                        effectiveStream1,
                        effectiveStream2,
                        effectiveStream3),
                optEffectiveStream -> Assertions.assertThat(optEffectiveStream)
                        .hasValue(effectiveStream2));
    }

    @Test
    void determineEffectiveStream_multStrms_noneRecent() {

        // This stream is before lookup time so won't be picked
        final EffectiveMeta effectiveStream1 = buildEffectiveMeta(
                1, LOOKUP_TIME.plus(100, ChronoUnit.DAYS));
        // This is latest one for our lookup time
        final EffectiveMeta effectiveStream2 = buildEffectiveMeta(
                2, LOOKUP_TIME.minus(100, ChronoUnit.DAYS));
        final EffectiveMeta effectiveStream3 = buildEffectiveMeta(
                3, LOOKUP_TIME.minus(200, ChronoUnit.DAYS));

        doDetermineTest(
                Set.of(
                        effectiveStream1,
                        effectiveStream2,
                        effectiveStream3),
                optEffectiveStream -> Assertions.assertThat(optEffectiveStream)
                        .hasValue(effectiveStream2));
    }

    @Test
    void determineEffectiveStream_multStrms_someRecent() {

        // This stream is before lookup time so won't be picked
        final EffectiveMeta effectiveStream1 = buildEffectiveMeta(
                1, LOOKUP_TIME.plus(1, ChronoUnit.HOURS));
        // This is latest one for our lookup time
        final EffectiveMeta effectiveStream2 = buildEffectiveMeta(
                2, LOOKUP_TIME.minus(1, ChronoUnit.HOURS));
        final EffectiveMeta effectiveStream3 = buildEffectiveMeta(
                3, LOOKUP_TIME.minus(2, ChronoUnit.HOURS));
        final EffectiveMeta effectiveStream4 = buildEffectiveMeta(
                4, LOOKUP_TIME.minus(100, ChronoUnit.DAYS));
        final EffectiveMeta effectiveStream5 = buildEffectiveMeta(
                5, LOOKUP_TIME.minus(200, ChronoUnit.DAYS));

        doDetermineTest(
                Set.of(
                        effectiveStream1,
                        effectiveStream2,
                        effectiveStream3),
                optEffectiveStream -> {
                    Assertions.assertThat(optEffectiveStream)
                            .hasValue(effectiveStream2);
                });
    }

    private EffectiveMeta buildEffectiveMeta(final long id, final Instant effectiveTime) {
        return new EffectiveMeta(id, DUMMY_FEED, DUMMY_TYPE, effectiveTime.toEpochMilli());
    }

    void doDetermineTest(final Set<EffectiveMeta> effectiveStreams,
                         final Consumer<Optional<EffectiveMeta>> resultConsumer) {

        final Builder builder = EffectiveMetaSet.builder(DUMMY_FEED, DUMMY_TYPE);
        effectiveStreams.forEach(builder::add);

        Mockito.when(mockEffectiveStreamCache.get(Mockito.any()))
                .thenReturn(builder.build());

        final EffectiveStreamService effectiveStreamService = new EffectiveStreamService(mockEffectiveStreamCache);
        final Optional<EffectiveMeta> optEffectiveStream = effectiveStreamService.determineEffectiveStream(
                pipelineReference,
                LOOKUP_TIME_MS,
                RESULT);

        resultConsumer.accept(optEffectiveStream);
    }
}
