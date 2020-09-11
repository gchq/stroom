package stroom.dashboard.shared;

import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.RandomId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@JsonPropertyOrder({
        "id",
        "expression",
        "hide",
        "backgroundColor",
        "textColor",
        "enabled"})
@JsonInclude(Include.NON_NULL)
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "conditionFormattingRule", propOrder = {
        "id",
        "expression",
        "hide",
        "backgroundColor",
        "textColor",
        "enabled"})
public class ConditionalFormattingRule implements Serializable {
    @XmlElement(name = "id")
    @JsonProperty("id")
    private String id;
    @XmlElement(name = "expression")
    @JsonProperty("expression")
    private ExpressionOperator expression;
    @XmlElement(name = "hide")
    @JsonProperty("hide")
    private boolean hide;
    @XmlElement(name = "backgroundColor")
    @JsonProperty("backgroundColor")
    private String backgroundColor;
    @XmlElement(name = "textColor")
    @JsonProperty("textColor")
    private String textColor;
    @XmlElement(name = "enabled")
    @JsonProperty("enabled")
    private boolean enabled;

    public ConditionalFormattingRule() {
        // Default constructor necessary for GWT serialisation.
    }

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

    public void setId(final String id) {
        this.id = id;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    public boolean isHide() {
        return hide;
    }

    public void setHide(final boolean hide) {
        this.hide = hide;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(final String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(final String textColor) {
        this.textColor = textColor;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
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

    public static class Builder {
        private String id;
        private ExpressionOperator expression;
        private boolean hide;
        private String backgroundColor;
        private String textColor;
        private boolean enabled;

        public Builder() {
        }

        public Builder(final ConditionalFormattingRule rule) {
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
                id = RandomId.createId(5);
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
