package stroom.job.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "type",
        "expression"
})
@JsonInclude(Include.NON_NULL)
public class Schedule {

    @JsonProperty
    private final ScheduleType type;
    @JsonProperty
    private final String expression;

    @JsonCreator
    public Schedule(@JsonProperty("type") final ScheduleType type,
                    @JsonProperty("expression") final String expression) {
        this.type = type;
        this.expression = expression;
    }

    public ScheduleType getType() {
        return type;
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Schedule schedule = (Schedule) o;
        return type == schedule.type &&
                Objects.equals(expression, schedule.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, expression);
    }

    @Override
    public String toString() {
        return "Schedule{" +
                "type=" + type +
                ", expression='" + expression + '\'' +
                '}';
    }
}
