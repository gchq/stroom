package stroom.analytics.impl;

import stroom.analytics.shared.EmailContent;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.JinjavaConfig;
import com.hubspot.jinjava.interpret.RenderResult;
import com.hubspot.jinjava.interpret.TemplateError;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class RuleEmailTemplatingService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RuleEmailTemplatingService.class);

    EmailContent renderAlertEmail(
            final Detection detection,
            final NotificationEmailDestination emailDestination) {
        Objects.requireNonNull(detection);
        Objects.requireNonNull(emailDestination);

        final Jinjava jinjava = getJinjava();
        final Map<String, Object> context = buildContext(detection);

        final String subject = render(jinjava, context, emailDestination.getSubjectTemplate());
        final String body = render(jinjava, context, emailDestination.getBodyTemplate());

        return new EmailContent(subject, body);
    }

    @NotNull
    private static Jinjava getJinjava() {
        final JinjavaConfig config = JinjavaConfig.newBuilder()
                .withFailOnUnknownTokens(true)
                .build();
        final Jinjava jinjava = new Jinjava(config);
        return jinjava;
    }

    String renderTemplate(final Detection detection,
                          final String template) {
        Objects.requireNonNull(detection);
        Objects.requireNonNull(template);

        final Jinjava jinjava = getJinjava();
        final Map<String, Object> context = buildContext(detection);

        return render(jinjava, context, template);
    }

    private String render(final Jinjava jinjava,
                          final Map<String, Object> context,
                          final String template) {
        if (NullSafe.isBlankString(template)) {
            return template;
        } else {
            final RenderResult renderResult = jinjava.renderForResult(template, context);

            if (renderResult.hasErrors()) {
                final String msg = errorsToString(renderResult.getErrors());
                throw new RuntimeException(msg);
            }
            final String output = renderResult.getOutput();
            LOGGER.debug("""
                    Render result:
                    --Template:---------------------------------------------------------------------
                    {}
                    --Output:-----------------------------------------------------------------------
                    {}
                    --------------------------------------------------------------------------------
                    """, template, output);
            if (NullSafe.isBlankString(output)) {
                throw new RuntimeException("Blank output for template\n" + template);
            }
            return output;
        }
    }

    private String errorsToString(final List<TemplateError> errors) {
        if (NullSafe.isEmptyCollection(errors)) {
            return null;
        } else {
            final String prefix = errors.size() > 1
                    ? "The following errors were found when processing the template:"
                    : "The following error was found when processing the template:";
            final String detail = errors.stream()
                    .map(error ->
                            error.getSeverity() + ": "
                                    + error.getMessage()
                                    + ", at " + error.getLineno() + ":" + error.getStartPosition()
                                    + ", reason: " + error.getReason()
                                    + ", fieldName: " + error.getFieldName())
                    .collect(Collectors.joining("\n"));
            return prefix + "\n" + detail;
        }
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
            // Collectors.toMap() doesn't like null values, shame
            final Map<String, String> map = new HashMap<>(values.size());
            values.stream()
                    .filter(Objects::nonNull)
                    .filter(detectionValue -> detectionValue.getName() != null)
                    .forEach(detectionValue ->
                            map.put(detectionValue.getName(), detectionValue.getValue()));
            if (!map.isEmpty()) {
                context.put("values", map);
            }
        });
        NullSafe.consume(
                detection.getLinkedEvents(),
                linkedEvents -> context.put("linkedEvents", linkedEvents));
        LOGGER.debug("Context: {}", context);
        return context;
    }


    // --------------------------------------------------------------------------------


    public enum Mode {
        LOG,
        THROW
    }
}
