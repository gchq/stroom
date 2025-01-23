package stroom.util;

import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.Timer;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetrics {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetrics.class);

    private final String name = Metrics.buildName(getClass(), "foo", "bar");

    @BeforeEach
    void setUp() {
        // This normally gets done by dropwizard on boot
        try {
            SharedMetricRegistries.getDefault();
        } catch (IllegalStateException e) {
            SharedMetricRegistries.setDefault("myDefault", new MetricRegistry());
        }
        SharedMetricRegistries.getDefault().remove(name);
    }

    @Test
    void testGauge() {
        final AtomicLong atomicLong = new AtomicLong();
        Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .gauge(atomicLong::longValue)
                .register();
        atomicLong.set(123L);

        final Gauge<Long> gauge = Metrics.getRegistry().gauge(name);

        assertThat(gauge.getValue())
                .isEqualTo(123L);

        atomicLong.set(456L);

        assertThat(gauge.getValue())
                .isEqualTo(456L);
    }

    @Test
    void testCounter() {
        final Counter counter = Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .counter()
                .createAndRegister();

        final Counter counter2 = Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .counter()
                .createAndRegister();

        assertThat(counter2)
                .isSameAs(counter);

        assertThat(Metrics.getRegistry().counter(name))
                .isSameAs(counter);

        assertThat(counter.getCount())
                .isEqualTo(0);
        counter.inc();
        assertThat(counter.getCount())
                .isEqualTo(1);
        counter.inc(10);
        assertThat(counter.getCount())
                .isEqualTo(11);
    }

    @Test
    void testMeter() {
        final Meter meter = Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .meter()
                .createAndRegister();

        final Meter meter2 = Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .meter()
                .createAndRegister();

        assertThat(meter2)
                .isSameAs(meter);
        assertThat(Metrics.getRegistry().meter(name))
                .isSameAs(meter);

        for (int i = 0; i < 15; i++) {
            meter.mark();
        }
        assertThat(meter.getCount())
                .isEqualTo(15);
        ThreadUtil.sleep(1000);
        // 15 in 1sec so >10/s
        assertThat(meter.getMeanRate())
                .isGreaterThan(10)
                .isLessThan(20);
    }

    @Test
    void testTimer() {
        final Timer timer = Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .timer()
                .createAndRegister();

        final Timer timer2 = Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .timer()
                .createAndRegister();

        assertThat(timer2)
                .isSameAs(timer);
        assertThat(Metrics.getRegistry().timer(name))
                .isSameAs(timer);

        assertThat(timer.getCount())
                .isEqualTo(0);

        timer.time(() -> {
            ThreadUtil.sleep(100);
        });

        timer.time(() -> {
            ThreadUtil.sleep(200);
        });

        LOGGER.info("values: {}", timer.getSnapshot().getValues());

        assertThat(timer.getCount())
                .isEqualTo(2);

        assertThat(timer.getSnapshot().getMin())
                .isCloseTo(TimeUnit.MILLISECONDS.toNanos(100),
                        Percentage.withPercentage(10));

        assertThat(timer.getSnapshot().getMax())
                .isCloseTo(TimeUnit.MILLISECONDS.toNanos(200),
                        Percentage.withPercentage(10));
    }

    @Test
    void testHistogram() {
        final Histogram histogram = Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .histogram()
                .createAndRegister();

        final Histogram histogram2 = Metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .histogram()
                .createAndRegister();

        assertThat(histogram2)
                .isSameAs(histogram);
        assertThat(Metrics.getRegistry().histogram(name))
                .isSameAs(histogram);

        histogram.update(5);
        histogram.update(10);
        histogram.update(13);
        histogram.update(3);
        histogram.update(20);
        // In order: 3, 5, 10, 13, 20

        assertThat(histogram.getCount())
                .isEqualTo(5);

        assertThat(histogram.getSnapshot().getMin())
                .isEqualTo(3);
        assertThat(histogram.getSnapshot().getMax())
                .isEqualTo(20);
        assertThat(histogram.getSnapshot().getMedian())
                .isEqualTo(10);
    }
}
