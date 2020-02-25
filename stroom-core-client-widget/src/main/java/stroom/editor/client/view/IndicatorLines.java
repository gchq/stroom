package stroom.editor.client.view;

import stroom.util.shared.Indicator;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class IndicatorLines {
    private final Indicators indicators;
    private final Map<Integer, Indicator> map;

    public IndicatorLines(final Indicators indicators) {
        this.indicators = indicators;
        map = new HashMap<>();
        if (indicators.getErrorList() != null) {
            for (final StoredError storedError : indicators.getErrorList()) {
                int lineNo = 1;
                if (storedError.getLocation() != null) {
                    lineNo = storedError.getLocation().getLineNo();
                }
                if (lineNo <= 0) {
                    lineNo = 1;
                }

                map.computeIfAbsent(lineNo, k -> new Indicator()).add(storedError.getSeverity(), storedError);
            }
        }
    }

    public Severity getMaxSeverity() {
        if (indicators.getErrorCount() != null) {
            for (final Severity sev : Severity.SEVERITIES) {
                final Integer c = indicators.getErrorCount().get(sev);
                if (c != null && c > 0) {
                    return sev;
                }
            }
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
        if (indicators.getErrorCount() != null) {
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
}
