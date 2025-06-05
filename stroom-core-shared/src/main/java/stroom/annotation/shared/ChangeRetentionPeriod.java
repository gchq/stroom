package stroom.annotation.shared;

import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class ChangeRetentionPeriod extends AbstractAnnotationChange {
    @JsonProperty
    private final SimpleDuration retentionPeriod;

    @JsonCreator
    public ChangeRetentionPeriod(@JsonProperty("retentionPeriod") final SimpleDuration retentionPeriod) {
        this.retentionPeriod = retentionPeriod;
    }

    public SimpleDuration getRetentionPeriod() {
        return retentionPeriod;
    }
}
