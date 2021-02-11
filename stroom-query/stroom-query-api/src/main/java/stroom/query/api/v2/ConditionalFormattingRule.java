package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "id",
        "expression",
        "hide",
        "backgroundColor",
        "textColor",
        "enabled"})
@JsonInclude(Include.NON_NULL)
public class ConditionalFormattingRule {

    @JsonProperty("id")
    private final String id;
    @JsonProperty("expression")
    private final ExpressionOperator expression;
    @JsonProperty("hide")
    private final boolean hide;
    @JsonProperty("backgroundColor")
    private final String backgroundColor;
    @JsonProperty("textColor")
    private final String textColor;
    @JsonProperty("enabled")
    private final boolean enabled;

    @JsonCreator
    public ConditionalFormattingRule(@JsonProperty("id") final String id,
                                     @JsonProperty("expression") final ExpressionOperator expression,
                                     @JsonProperty("hide") final boolean hide,
                                     @JsonProperty("backgroundColor") final String backgroundColor,
                                     @JsonProperty("textColor") final String textColor,
                                     @JsonProperty("enabled") final boolean enabled) {
        this.id = id;
        this.expression = expression;
        this.hide = hide;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public boolean isHide() {
        return hide;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ConditionalFormattingRule that = (ConditionalFormattingRule) o;
        return hide == that.hide &&
                enabled == that.enabled &&
                Objects.equals(id, that.id) &&
                Objects.equals(expression, that.expression) &&
                Objects.equals(backgroundColor, that.backgroundColor) &&
                Objects.equals(textColor, that.textColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, expression, hide, backgroundColor, textColor, enabled);
    }

    @Override
    public String toString() {
        return "ConditionalFormattingRule{" +
                "id='" + id + '\'' +
                ", expression=" + expression +
                ", hide=" + hide +
                ", backgroundColor='" + backgroundColor + '\'' +
                ", textColor='" + textColor + '\'' +
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
        private ExpressionOperator expression;
        private boolean hide;
        private String backgroundColor;
        private String textColor;
        private boolean enabled;

        private Builder() {
        }

        private Builder(final ConditionalFormattingRule rule) {
            this.id = rule.id;
            this.expression = rule.expression;
            this.hide = rule.hide;
            this.backgroundColor = rule.backgroundColor;
            this.textColor = rule.textColor;
            this.enabled = rule.enabled;
        }

        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        public Builder expression(final ExpressionOperator expression) {
            this.expression = expression;
            return this;
        }

        public Builder hide(final boolean hide) {
            this.hide = hide;
            return this;
        }

        public Builder backgroundColor(final String backgroundColor) {
            this.backgroundColor = backgroundColor;
            return this;
        }

        public Builder textColor(final String textColor) {
            this.textColor = textColor;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ConditionalFormattingRule build() {
            if (id == null) {
                throw new NullPointerException("Null rule id");
            }

            return new ConditionalFormattingRule(
                    id,
                    expression,
                    hide,
                    backgroundColor,
                    textColor,
                    enabled);
        }
    }
}
