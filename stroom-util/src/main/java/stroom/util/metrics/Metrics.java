package stroom.util.metrics;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

/**
 * Interface for creating and registering {@link com.codahale.metrics.Metric}s.
 * <p>
 * Do not use {@link SharedMetricRegistries#getDefault()} as this relies on a static
 * {@link MetricRegistry} that causes problems in tests.
 * </p>
 */
public interface Metrics {

    LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HasMetrics.class);

    String DELTA = "delta";
    String READ = "read";
    String WRITE = "write";
    String POSITION = "position";
    String SIZE = "size";
    String COUNT = "count";
    String AGE_MS = "ageMs";
    String FILE_COUNT = "fileCount";
    String SIZE_IN_BYTES = "sizeInBytes";

    MetricRegistry getRegistry();

    /**
     * @param clazz The class that owns the metric. The fully qualified class name
     *              will be used as the prefix in the metric name.
     */
    default MetricRegistrationBuilder registrationBuilder(final Class<?> clazz) {
        return new MetricRegistrationBuilder(getRegistry(), clazz);
    }
}
