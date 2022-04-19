package stroom.proxy.repo;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ScheduledPragma extends AbstractConfig {

    private final String statement;
    private final StroomDuration frequency;

    @JsonCreator
    public ScheduledPragma(@JsonProperty("statement") final String statement,
                           @JsonProperty("frequency") final StroomDuration frequency) {
        this.statement = statement;
        this.frequency = frequency;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    public String getStatement() {
        return statement;
    }

    @RequiresRestart(value = RestartScope.SYSTEM)
    @JsonProperty
    public StroomDuration getFrequency() {
        return frequency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String statement;
        private StroomDuration frequency;

        private Builder() {
        }

        public Builder statement(final String statement) {
            this.statement = statement;
            return this;
        }

        public Builder frequency(final StroomDuration frequency) {
            this.frequency = frequency;
            return this;
        }

        public ScheduledPragma build() {
            return new ScheduledPragma(statement, frequency);
        }
    }
}
