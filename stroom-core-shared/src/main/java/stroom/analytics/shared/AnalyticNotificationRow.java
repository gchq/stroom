package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticNotificationRow {

    @JsonProperty
    private final AnalyticNotification analyticNotification;
    @JsonProperty
    private final AnalyticNotificationState analyticNotificationState;

    @JsonCreator
    public AnalyticNotificationRow(
            @JsonProperty("analyticNotification") final AnalyticNotification analyticNotification,
            @JsonProperty("analyticNotificationState") final AnalyticNotificationState analyticNotificationState) {
        this.analyticNotification = analyticNotification;
        this.analyticNotificationState = analyticNotificationState;
    }

    public AnalyticNotification getAnalyticNotification() {
        return analyticNotification;
    }

    public AnalyticNotificationState getAnalyticNotificationState() {
        return analyticNotificationState;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticNotificationRow that = (AnalyticNotificationRow) o;
        return Objects.equals(analyticNotification.getUuid(), that.analyticNotification.getUuid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(analyticNotification.getUuid());
    }
}
