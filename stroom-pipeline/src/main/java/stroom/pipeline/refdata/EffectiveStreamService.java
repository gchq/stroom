package stroom.pipeline.refdata;

import stroom.meta.api.EffectiveMeta;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;
import stroom.util.shared.StringUtil;

import java.time.Instant;
import java.util.Arrays;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class EffectiveStreamService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EffectiveStreamService.class);

    private EffectiveStreamCache effectiveStreamCache;

    @Inject
    public EffectiveStreamService(final EffectiveStreamCache effectiveStreamCache) {
        this.effectiveStreamCache = effectiveStreamCache;
    }

    NavigableSet<EffectiveMeta> get(final EffectiveStreamKey effectiveStreamKey) {
        return effectiveStreamCache.get(effectiveStreamKey);
    }

    /**
     * Find the most recent stream that is older than or equal to {@code time} or empty if
     * there isn't one
     */
    Optional<EffectiveMeta> determineEffectiveStream(final PipelineReference pipelineReference,
                                                       final long time,
                                                       final ReferenceDataResult result) {

        LOGGER.trace(() -> LogUtil.message("determineEffectiveStream({}, {})",
                pipelineReference, Instant.ofEpochMilli(time)));

        // Create a key to find a set of effective times in the pool.
        final EffectiveStreamKey effectiveStreamKey = EffectiveStreamKey.forLookupTime(
                pipelineReference.getFeed().getName(),
                pipelineReference.getStreamType(),
                time);

        // Try and fetch a tree set of effective streams for this key.
        final NavigableSet<EffectiveMeta> effectiveStreams = effectiveStreamCache.get(effectiveStreamKey);

        final EffectiveMeta effectiveStream;

        if (effectiveStreams != null && effectiveStreams.size() > 0) {
            result.logLazyTemplate(Severity.INFO,
                    "Found {} potential effective stream{} (spanning {} => {}) " +
                            "for feed: '{}', type: '{}', window: {} => {}",
                    () -> Arrays.asList(
                            effectiveStreams.size(),
                            StringUtil.pluralSuffix(effectiveStreams.size()),
                            Instant.ofEpochMilli(effectiveStreams.first().getEffectiveMs()),
                            Instant.ofEpochMilli(effectiveStreams.last().getEffectiveMs()),
                            effectiveStreamKey.getFeed(),
                            effectiveStreamKey.getStreamType(),
                            Instant.ofEpochMilli(effectiveStreamKey.getFromMs()),
                            Instant.ofEpochMilli(effectiveStreamKey.getToMs())));

            if (LOGGER.isTraceEnabled()) {
                final String streams = effectiveStreams.stream()
                        .map(effectiveMeta -> "  " + effectiveMeta.toString())
                        .collect(Collectors.joining("\n"));
                LOGGER.trace("For key {}, comparing time: {} to effective streams:\n{}",
                        effectiveStreamKey, Instant.ofEpochMilli(time), streams);
            }

            // Try and find the stream before the requested time that is less
            // than or equal to it.
            effectiveStream = EffectiveMeta.findLatestBefore(time, effectiveStreams);
        } else {
            // No need to log here as it will get logged by the caller
            effectiveStream = null;
        }

        if (effectiveStream == null) {
            result.logLazyTemplate(
                    Severity.WARNING,
                    "No effective stream can be found for feed '{}' and lookup time '{}'. " +
                            "Check a reference data stream exists with an effective time that is before the " +
                            "lookup time.",
                    () -> Arrays.asList(pipelineReference.getFeed().getName(),
                            Instant.ofEpochMilli(time).toString()));
        }
        return Optional.ofNullable(effectiveStream);
    }

    /**
     * For testing
     */
    void setEffectiveStreamCache(final EffectiveStreamCache effectiveStreamcache) {
        this.effectiveStreamCache = effectiveStreamcache;
    }
}
