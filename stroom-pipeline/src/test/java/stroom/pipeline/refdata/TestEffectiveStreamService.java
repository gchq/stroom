package stroom.pipeline.refdata;

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
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
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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
    @Mock
    private EffectiveStreamCache mockEffectiveStreamCache;

    private PipelineReference pipelineReference;

    @BeforeEach
    void setUp() {
        pipelineReference = new PipelineReference();
        pipelineReference.setFeed(DocRef.builder()
                .name(FEED_NAME)
                .type(FeedDoc.DOCUMENT_TYPE)
                .build());
        pipelineReference.setStreamType(StreamTypeNames.REFERENCE);
    }

    @Test
    void determineEffectiveStream_noStreamsFound() {

        final EffectiveStreamService effectiveStreamService = new EffectiveStreamService(mockEffectiveStreamCache);

        final long lookupTimeMs = LocalDateTime.of(2020, 6, 1, 11, 0)
                .toEpochSecond(ZoneOffset.UTC);
        final LookupIdentifier lookupIdentifier = new LookupIdentifier("MY_MAP", "MY_KEY", lookupTimeMs);
        final ReferenceDataResult result = new ReferenceDataResult(lookupIdentifier);

        Mockito.when(mockEffectiveStreamCache.get(Mockito.any()))
                .thenReturn(Collections.emptyNavigableSet());

        final Optional<EffectiveStream> optEffectiveStream = effectiveStreamService.determineEffectiveStream(
                pipelineReference,
                lookupTimeMs,
                result);

        Assertions.assertThat(optEffectiveStream)
                .isEmpty();
    }

    @Test
    void determineEffectiveStream_oneStreamFound_sameDay() {

        final EffectiveStream effectiveStream1 = EffectiveStream.of(
                1, LOOKUP_TIME.minus(1, ChronoUnit.HOURS));

        doDetermineTest(Set.of(
                        effectiveStream1),
                optEffectiveStream -> {
                    Assertions.assertThat(optEffectiveStream)
                            .hasValue(effectiveStream1);
                });
    }

    @Test
    void determineEffectiveStream_oneStreamFound_veryOld() {

        final EffectiveStream effectiveStream1 = EffectiveStream.of(
                1, LOOKUP_TIME.minus(100, ChronoUnit.DAYS));

        doDetermineTest(
                Set.of(
                        effectiveStream1),
                optEffectiveStream -> {
                    Assertions.assertThat(optEffectiveStream)
                            .hasValue(effectiveStream1);
                });
    }

    @Test
    void determineEffectiveStream_multStrms_allRecent() {

        // This stream is before lookup time so won't be picked
        final EffectiveStream effectiveStream1 = EffectiveStream.of(
                1, LOOKUP_TIME.plus(1, ChronoUnit.HOURS));
        // This is latest one for our lookup time
        final EffectiveStream effectiveStream2 = EffectiveStream.of(
                2, LOOKUP_TIME.minus(1, ChronoUnit.HOURS));
        final EffectiveStream effectiveStream3 = EffectiveStream.of(
                3, LOOKUP_TIME.minus(2, ChronoUnit.HOURS));

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

    @Test
    void determineEffectiveStream_multStrms_noneRecent() {

        // This stream is before lookup time so won't be picked
        final EffectiveStream effectiveStream1 = EffectiveStream.of(
                1, LOOKUP_TIME.plus(100, ChronoUnit.DAYS));
        // This is latest one for our lookup time
        final EffectiveStream effectiveStream2 = EffectiveStream.of(
                2, LOOKUP_TIME.minus(100, ChronoUnit.DAYS));
        final EffectiveStream effectiveStream3 = EffectiveStream.of(
                3, LOOKUP_TIME.minus(200, ChronoUnit.DAYS));

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

    @Test
    void determineEffectiveStream_multStrms_someRecent() {

        // This stream is before lookup time so won't be picked
        final EffectiveStream effectiveStream1 = EffectiveStream.of(
                1, LOOKUP_TIME.plus(1, ChronoUnit.HOURS));
        // This is latest one for our lookup time
        final EffectiveStream effectiveStream2 = EffectiveStream.of(
                2, LOOKUP_TIME.minus(1, ChronoUnit.HOURS));
        final EffectiveStream effectiveStream3 = EffectiveStream.of(
                3, LOOKUP_TIME.minus(2, ChronoUnit.HOURS));
        final EffectiveStream effectiveStream4 = EffectiveStream.of(
                4, LOOKUP_TIME.minus(100, ChronoUnit.DAYS));
        final EffectiveStream effectiveStream5 = EffectiveStream.of(
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

    void doDetermineTest(final Set<EffectiveStream> effectiveStreams,
                         final Consumer<Optional<EffectiveStream>> resultConsumer) {

        Mockito.when(mockEffectiveStreamCache.get(Mockito.any()))
                .thenReturn(new TreeSet<>(effectiveStreams));

        final EffectiveStreamService effectiveStreamService = new EffectiveStreamService(mockEffectiveStreamCache);
        final Optional<EffectiveStream> optEffectiveStream = effectiveStreamService.determineEffectiveStream(
                pipelineReference,
                LOOKUP_TIME_MS,
                RESULT);

        resultConsumer.accept(optEffectiveStream);
    }
}
