package stroom.util.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static stroom.util.shared.Severity.HIGH_TO_LOW_COMPARATOR;

public class ErrorMessages {

    private final List<ErrorMessage> errorMessages;

    public ErrorMessages(final List<ErrorMessage> errorMessages) {
        this.errorMessages = errorMessages;
    }

    public List<ErrorMessage> getErrorMessages() {
        return errorMessages == null ? Collections.emptyList() : errorMessages;
    }

    public Severity getHighestSeverity() {
        return asMap().keySet().stream().sorted(HIGH_TO_LOW_COMPARATOR).findFirst().orElse(null);
    }

    public boolean containsAny(final Severity...severities) {
        for (final Severity severity : severities) {
            if (!get(severity).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return getErrorMessages().isEmpty();
    }

    public List<String> get(final Severity...severities) {
        final List<String> messages = new ArrayList<>();
        for (final Severity severity : severities) {
            messages.addAll(asMap().getOrDefault(severity, new ArrayList<>()));
        }
        return messages;
    }

    private Map<Severity, List<String>> asMap() {
        return NullSafe.stream(errorMessages)
                .collect(Collectors.groupingBy(ErrorMessage::getSeverity, HashMap::new,
                        Collectors.mapping(ErrorMessage::getMessage, Collectors.toList())));
    }
}
