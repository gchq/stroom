package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
        "enabled",
        "style",
        "customStyle"})
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

    @JsonProperty("style")
    private final ConditionalFormattingStyle style;
    @JsonProperty("customStyle")
    private final Boolean customStyle;

    @JsonCreator
    public ConditionalFormattingRule(@JsonProperty("id") final String id,
                                     @JsonProperty("expression") final ExpressionOperator expression,
                                     @JsonProperty("hide") final boolean hide,
                                     @JsonProperty("backgroundColor") final String backgroundColor,
                                     @JsonProperty("textColor") final String textColor,
                                     @JsonProperty("enabled") final boolean enabled,
                                     @JsonProperty("style") final ConditionalFormattingStyle style,
                                     @JsonProperty("customStyle") final Boolean customStyle) {
        this.id = id;
        this.expression = expression;
        this.hide = hide;
        this.backgroundColor = backgroundColor;
        this.textColor = textColor;
        this.enabled = enabled;
        this.style = style;
        this.customStyle = customStyle;
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

    public ConditionalFormattingStyle getStyle() {
        return style;
    }

    public Boolean getCustomStyle() {
        return customStyle;
    }

    @JsonIgnore
    public boolean isCustomStyle() {
        return customStyle != Boolean.FALSE;
    }

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
                Objects.equals(textColor, that.textColor) &&
                Objects.equals(style, that.style) &&
                Objects.equals(customStyle, that.customStyle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, expression, hide, backgroundColor, textColor, enabled, style, customStyle);
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
                ", styleName=" + style +
                ", customStyle=" + customStyle +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String id;
        private ExpressionOperator expression;
        private boolean hide;
        private String backgroundColor;
        private String textColor;
        private boolean enabled;
        private ConditionalFormattingStyle style;
        private Boolean customStyle;


        private Builder() {
        }

        private Builder(final ConditionalFormattingRule rule) {
            this.id = rule.id;
            this.expression = rule.expression;
            this.hide = rule.hide;
            this.backgroundColor = rule.backgroundColor;
            this.textColor = rule.textColor;
            this.enabled = rule.enabled;
            this.style = rule.style;
            this.customStyle = rule.customStyle;
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
            if (backgroundColor.trim().length() == 0) {
                this.backgroundColor = null;
            } else {
                this.backgroundColor = backgroundColor.trim();
            }
            return this;
        }

        public Builder textColor(final String textColor) {
            if (textColor.trim().length() == 0) {
                this.textColor = null;
            } else {
                this.textColor = textColor.trim();
            }
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder style(final ConditionalFormattingStyle style) {
            this.style = style;
            return this;
        }

        public Builder customStyle(final Boolean customStyle) {
            if (customStyle != Boolean.FALSE) {
                this.customStyle = null;
            } else {
                this.customStyle = false;
            }
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
                    enabled,
                    style,
                    customStyle);
        }
    }
}
