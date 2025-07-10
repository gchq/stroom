package stroom.importexport.shared;

import stroom.util.shared.Message;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class ExportSummary {

    @JsonProperty
    private final Map<String, Integer> successCountsByType;
    @JsonProperty
    private final Map<String, Integer> failedCountsByType;
    @JsonProperty
    private final List<Message> messages;

    @JsonCreator
    public ExportSummary(@JsonProperty("successCountsByType") final Map<String, Integer> successCountsByType,
                         @JsonProperty("failedCountsByType") final Map<String, Integer> failedCountsByType,
                         @JsonProperty("messages") final List<Message> messages) {
        this.successCountsByType = Collections.unmodifiableMap(NullSafe.map(successCountsByType));
        this.failedCountsByType = Collections.unmodifiableMap(NullSafe.map(failedCountsByType));
        this.messages = NullSafe.unmodifiableList(messages);
    }

    public List<Message> getMessages() {
        return messages;
    }

    public Map<String, Integer> getFailedCountsByType() {
        return failedCountsByType;
    }

    public Map<String, Integer> getSuccessCountsByType() {
        return successCountsByType;
    }

    @JsonIgnore
    public int getSuccessTotal() {
        return successCountsByType.values()
                .stream()
                .mapToInt(i -> i)
                .sum();
    }

    @JsonIgnore
    public int getFailedTotal() {
        return failedCountsByType.values()
                .stream()
                .mapToInt(i -> i)
                .sum();
    }

    @Override
    public String toString() {
        return "ExportSummary{" +
               "successCountsByType=" + successCountsByType +
               ", failedCountsByType=" + failedCountsByType +
               ", messages=" + messages +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private Map<String, Integer> successCountsByType = null;
        private Map<String, Integer> failedCountsByType = null;
        private List<Message> messages = Collections.emptyList();

        public void addSuccess(final String type) {
            if (successCountsByType == null) {
                successCountsByType = new HashMap<>();
            }
            successCountsByType.merge(type, 1, Integer::sum);
        }

        public void addFailure(final String type) {
            if (failedCountsByType == null) {
                failedCountsByType = new HashMap<>();
            }
            failedCountsByType.merge(type, 1, Integer::sum);
        }

        public void setMessages(final List<Message> messages) {
            this.messages = messages;
        }

        public ExportSummary build() {
            return new ExportSummary(successCountsByType, failedCountsByType, messages);
        }
    }
}
