/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.metrics;

import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

public class TestMetricsImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetricsImpl.class);

    private final String name = MetricsUtil.buildName(getClass(), "foo", "bar");

    private Metrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new MetricsImpl(new MetricRegistry());
    }

    @Test
    void testGauge() {
        final AtomicLong atomicLong = new AtomicLong();
        metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .gauge(atomicLong::longValue)
                .register();
        atomicLong.set(123L);

        final Gauge<Long> gauge = metrics.getRegistry().gauge(name);

        assertThat(gauge.getValue())
                .isEqualTo(123L);

        atomicLong.set(456L);

        assertThat(gauge.getValue())
                .isEqualTo(456L);
    }

    @Test
    void testCounter() {
        final Counter counter = metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .counter()
                .createAndRegister();

        final Counter counter2 = metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .counter()
                .createAndRegister();

        assertThat(counter2)
                .isSameAs(counter);

        assertThat(metrics.getRegistry().counter(name))
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
        final Meter meter = metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .meter()
                .createAndRegister();

        final Meter meter2 = metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .meter()
                .createAndRegister();

        assertThat(meter2)
                .isSameAs(meter);
        assertThat(metrics.getRegistry().meter(name))
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
        final Timer timer = metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .timer()
                .createAndRegister();

        final Timer timer2 = metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .timer()
                .createAndRegister();

        assertThat(timer2)
                .isSameAs(timer);
        assertThat(metrics.getRegistry().timer(name))
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
                        Percentage.withPercentage(30));

        assertThat(timer.getSnapshot().getMax())
                .isCloseTo(TimeUnit.MILLISECONDS.toNanos(200),
                        Percentage.withPercentage(30));
    }

    @Test
    void testHistogram() {
        final Histogram histogram = metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .histogram()
                .createAndRegister();

        final Histogram histogram2 = metrics.registrationBuilder(getClass())
                .addNamePart("foo")
                .addNamePart("bar")
                .histogram()
                .createAndRegister();

        assertThat(histogram2)
                .isSameAs(histogram);
        assertThat(metrics.getRegistry().histogram(name))
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
