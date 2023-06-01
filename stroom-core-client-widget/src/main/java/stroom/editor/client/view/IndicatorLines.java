package stroom.editor.client.view;

import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IndicatorLines {

    private final Indicators indicators;
    private final Map<Integer, Indicator> map;
    private final Indicator locationAgnosticIndicator;

    public IndicatorLines(final Indicators indicators) {
        this.indicators = indicators;
        this.map = new HashMap<>();
        this.locationAgnosticIndicator = new Indicator();

        if (indicators != null && indicators.getErrorList() != null) {
            for (final StoredError storedError : indicators.getErrorList()) {
//                int lineNo = 1;
                if (storedError.getLocation() != null && storedError.getLocation().getLineNo() > 0) {
                    map.computeIfAbsent(storedError.getLocation().getLineNo(), k -> new Indicator())
                            .add(storedError.getSeverity(), storedError);
//                    lineNo = storedError.getLocation().getLineNo();
                } else {
                    locationAgnosticIndicator.add(storedError.getSeverity(), storedError);
                }
            }
        }
    }

    public Severity getMaxSeverity() {
        if (indicators != null && indicators.getErrorCount() != null) {
            final List<Severity> severities = new ArrayList<>();
            for (final Severity sev : Severity.SEVERITIES) {
                final Integer c = indicators.getErrorCount().get(sev);
                if (c != null && c > 0) {
                    severities.add(sev);
                    break;
                }
            }
            if (locationAgnosticIndicator != null) {
                final Severity maxSeverity = locationAgnosticIndicator.getMaxSeverity();
                if (maxSeverity != null) {
                    severities.add(maxSeverity);
                }
            }

            return Severity.getMaxSeverity(severities, null);
        }
        return null;
    }

    /**
     * Gets a summary of the counts of warnings, errors and fatal errors.
     *
     * @return A summary of the counts of warnings, errors and fatal errors.
     */
    public String getSummaryHTML() {
        final StringBuilder html = new StringBuilder();
        if (indicators != null && indicators.getErrorCount() != null) {
            for (final Severity severity : Severity.SEVERITIES) {
                final Integer count = indicators.getErrorCount().get(severity);
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
}
