package stroom.editor.client.view;

import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class IndicatorLines {

    private final Indicators allIndicators;
    private final Map<Integer, Indicator> map;
    private final Indicator locationAgnosticIndicator;

//    public IndicatorLines(final IndicatorLines indicatorLines) {
//        if (indicatorLines == null) {
//            this.allIndicators = new Indicators();
//            this.map = new HashMap<>();
//            this.locationAgnosticIndicator = new Indicator();
//        } else {
//            this.allIndicators = new Indicators(indicatorLines.allIndicators);
//            this.map = new HashMap<>(indicatorLines.map);
//            this.locationAgnosticIndicator = new Indicator(indicatorLines.getLocationAgnosticIndicator());
//        }
//        GWT.log("map(25) at end of ctor2:\n>> " + map.get(25));
//        GWT.log("locationAgnosticIndicator at end of ctor1:\n>> " + locationAgnosticIndicator);
//    }

    public IndicatorLines(final Indicators indicators) {
        this.allIndicators = indicators != null
                ? new Indicators(indicators)
                : new Indicators();
        this.map = new HashMap<>();
        this.locationAgnosticIndicator = new Indicator();

        if (indicators != null && indicators.getErrorList() != null) {
            for (final StoredError storedError : indicators.getErrorList()) {
//                int lineNo = 1;
                if (storedError.getLocation() != null && storedError.getLocation().getLineNo() > 0) {
                    map.computeIfAbsent(storedError.getLocation().getLineNo(), k -> new Indicator())
                            .add(storedError.getSeverity(), storedError);
//                    GWT.log("map(25) after add:\n>> " + map.get(25));
//                    lineNo = storedError.getLocation().getLineNo();
                } else {
                    locationAgnosticIndicator.add(storedError.getSeverity(), storedError);
//                    GWT.log("locationAgnosticIndicator after add:\n>> " + locationAgnosticIndicator);
                }
            }
        }
//        GWT.log("map(25) at end of ctor2:\n>> " + map.get(25));
//        GWT.log("locationAgnosticIndicator at end of ctor2:\n>> " + locationAgnosticIndicator);
    }

    public boolean isEmpty() {
        return allIndicators.isEmpty();
//        return locationAgnosticIndicator.isEmpty()
//                && map.isEmpty();
    }

    public Severity getMaxSeverity() {
        if (allIndicators != null && allIndicators.getErrorCount() != null) {
            final List<Severity> severities = new ArrayList<>();
            for (final Severity sev : Severity.SEVERITIES) {
                final Integer c = allIndicators.getErrorCount().get(sev);
                if (c != null && c > 0) {
                    severities.add(sev);
                    break;
                }
            }
//            if (locationAgnosticIndicator != null) {
//                final Severity maxSeverity = locationAgnosticIndicator.getMaxSeverity();
//                if (maxSeverity != null) {
//                    severities.add(maxSeverity);
//                }
//            }
//
            return Severity.getMaxSeverity(severities, null);
        }
        return null;
    }

    public int getCount(final Severity severity) {
        final Integer errorCount = allIndicators.getErrorCount().get(severity);
        return errorCount != null
                ? errorCount
                : 0;
//        return Stream.concat(map.values().stream(), Stream.of(locationAgnosticIndicator))
//                .mapToInt(indicator -> indicator.getCount(severity))
//                .sum();
    }

    /**
     * Gets a summary of the counts of warnings, errors and fatal errors.
     *
     * @return A summary of the counts of warnings, errors and fatal errors.
     */
    public String getSummaryHTML() {
        final StringBuilder html = new StringBuilder();
        if (allIndicators != null && allIndicators.getErrorCount() != null) {
            for (final Severity severity : Severity.SEVERITIES) {
                final Integer count = allIndicators.getErrorCount().get(severity);
                if (count != null && count > 0) {
                    html.append(severity.getDisplayValue());
                    html.append(": ");
                    html.append(count);
                    html.append("<br/>");
                }
            }
        }
        return html.toString();
    }

    public Collection<Integer> getLineNumbers() {
        return map.keySet();
    }

    public Indicator getIndicator(final int lineNo) {
        return map.get(lineNo);
    }

    public Indicator getLocationAgnosticIndicator() {
        return locationAgnosticIndicator;
    }

    @Override
    public String toString() {
        final Map<Severity, Set<StoredError>> combinedMap = new HashMap<>();
        combinedMap.putAll(locationAgnosticIndicator.getErrorMap());

        for (final Severity severity : Severity.values()) {
            final Set<StoredError> combinedErrors = combinedMap.computeIfAbsent(severity, k -> new HashSet<>());
            for (final Indicator indicator : map.values()) {
                final Set<StoredError> storedErrors = indicator.getErrorMap().get(severity);
                if (storedErrors != null) {
                    combinedErrors.addAll(storedErrors);
                }
            }
        }
        return Arrays.stream(Severity.SEVERITIES)
                .map(severity -> severity.name() + ":" + combinedMap.get(severity).size())
                .collect(Collectors.joining(", "));
    }
}
