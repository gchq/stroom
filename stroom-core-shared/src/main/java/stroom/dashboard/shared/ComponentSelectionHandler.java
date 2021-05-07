package stroom.dashboard.shared;

import stroom.query.api.v2.ExpressionOperator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "id",
        "componentId",
        "expression",
        "enabled"
})
@JsonInclude(Include.NON_NULL)
public class ComponentSelectionHandler {

    @JsonProperty("id")
    private final String id;
    @JsonProperty("componentId")
    private final String componentId;
    @JsonProperty("expression")
    private final ExpressionOperator expression;
    @JsonProperty("enabled")
    private final boolean enabled;

    @JsonCreator
    public ComponentSelectionHandler(@JsonProperty("id") final String id,
                                     @JsonProperty("componentId") final String componentId,
                                     @JsonProperty("expression") final ExpressionOperator expression,
                                     @JsonProperty("enabled") final boolean enabled) {
        this.id = id;
        this.componentId = componentId;
        this.expression = expression;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public String getComponentId() {
        return componentId;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComponentSelectionHandler)) {
            return false;
        }
        final ComponentSelectionHandler that = (ComponentSelectionHandler) o;
        return enabled == that.enabled && Objects.equals(id, that.id) && Objects.equals(componentId,
                that.componentId) && Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, componentId, expression, enabled);
    }

    @Override
    public String toString() {
        return "ComponentSelectionListener{" +
                "id='" + id + '\'' +
                ", componentId='" + componentId + '\'' +
                ", expression=" + expression +
                ", enabled=" + enabled +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String id;
        private String componentId;
        private ExpressionOperator expression;
        private boolean enabled;

        private Builder() {
        }

        private Builder(final ComponentSelectionHandler componentSelectionHandler) {
            this.id = componentSelectionHandler.id;
            this.componentId = componentSelectionHandler.componentId;
            this.expression = componentSelectionHandler.expression;
            this.enabled = componentSelectionHandler.enabled;
        }

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder componentId(final String componentId) {
            this.componentId = componentId;
            return this;
        }

        public Builder expression(final ExpressionOperator expression) {
            this.expression = expression;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ComponentSelectionHandler build() {
            return new ComponentSelectionHandler(id, componentId, expression, enabled);
        }
    }
}
