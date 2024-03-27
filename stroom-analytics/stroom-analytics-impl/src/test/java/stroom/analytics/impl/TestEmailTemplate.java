package stroom.analytics.impl;

import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.hubspot.jinjava.Jinjava;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class TestEmailTemplate {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEmailTemplate.class);

    @Test
    void test() {
        final Jinjava jinjava = new Jinjava();

        final Detection detection = Detection.builder()
                .withDetectTime(DateUtil.createNormalDateTimeString())
                .withDetectorName("My Detector")
                .withDetailedDescription("Stuff happened (detailed)")
                .withHeadline("My headline")
                .withFullDescription("Stuff happened (full)")
                .withDetectionRevision(123)
                .withDetectorVersion("detector v4.5.6")
                .withRandomDetectorUuid()
                .withDetectionUniqueId("My unique ID")
                .addValue("foo", "FOO!")
                .addValue("bar", "BAR!")
                .addLinkedEvents(new DetectionLinkedEvent("Prod", 1L, 1000L))
                .addLinkedEvents(new DetectionLinkedEvent("Prod", 1L, 1001L))
                .addLinkedEvents(new DetectionLinkedEvent("Prod", 2L, 2000L))
                .build();

        final Map<String, Object> context = buildContext(detection);

        @SuppressWarnings("checkstyle:LineLength") final String template = """
                <p>Detector <em>{{ detectorName }}</em> {{ detectorVersion }} fired at {{ detectTime }}</p>
                                
                {% if (values | length) > 0 %}
                  <p>Detail: {{ headline }}</p>
                  <p>
                    <ul>
                      {% for key, val in values.items() %}
                        <li><strong>{{ key | e }}</strong>: {{ val | e }}</li>
                      {% endfor %}
                    </ul>
                  </p>
                {% endif %}
                                
                {% if (linkedEvents | length) > 0 %}
                  <p>Linked Events:</p>
                  <p>
                    <ul>
                      {% for linkedEvent in linkedEvents %}
                        <li>Environment: {{ linkedEvent.stroom }}, Stream ID: {{ linkedEvent.streamId }}, Event ID: {{ linkedEvent.eventId }}</li>
                      {% endfor %}
                    </ul>
                  </p>
                {% endif %}
                """;

        final String output = jinjava.render(template, context);

        LOGGER.info("""
                output:
                --------------------------------------------------------------------------------
                {}
                --------------------------------------------------------------------------------
                """, output);
        ;
    }

    private Map<String, Object> buildContext(final Detection detection) {
        Objects.requireNonNull(detection);
        final Map<String, Object> context = new HashMap<>();

        NullSafe.consume(detection.getDetectorName(), val -> context.put("detectorName", val));
        NullSafe.consume(detection.getDetectTime(), val -> context.put("detectTime", val));
        NullSafe.consume(detection.getDetectorUuid(), val -> context.put("detectorUuid", val));
        NullSafe.consume(detection.getDetectorVersion(), val -> context.put("detectorVersion", val));
        NullSafe.consume(detection.getDetectorEnvironment(), val -> context.put("detectorEnvironment", val));
        NullSafe.consume(detection.getHeadline(), val -> context.put("headline", val));
        NullSafe.consume(detection.getDetailedDescription(), val -> context.put("detailedDescription", val));
        NullSafe.consume(detection.getFullDescription(), val -> context.put("fullDescription", val));
        NullSafe.consume(detection.getDetectionUniqueId(), val -> context.put("detectionUniqueId", val));
        NullSafe.consume(detection.getDetectionRevision(), val -> context.put("detectionRevision", val));
        NullSafe.consume(detection.getDefunct(), val -> context.put("defunct", val));

        NullSafe.consume(detection.getValues(), values -> {
            context.put("values", values.stream()
                    .collect(Collectors.toMap(DetectionValue::getName, DetectionValue::getValue)));
        });
        NullSafe.consume(detection.getLinkedEvents(), linkedEvents -> context.put("linkedEvents", linkedEvents));
        return context;
    }
}
