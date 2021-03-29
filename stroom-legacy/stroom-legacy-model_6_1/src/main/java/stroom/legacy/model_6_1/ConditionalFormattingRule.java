package stroom.legacy.model_6_1;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.Objects;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "conditionFormattingRule", propOrder = {
        "id",
        "expression",
        "hide",
        "backgroundColor",
        "textColor",
        "enabled"})
@Deprecated
public class ConditionalFormattingRule implements Serializable {
    @XmlElement(name = "id")
    private String id;
    @XmlElement(name = "expression")
    private ExpressionOperator expression;
    @XmlElement(name = "hide")
    private boolean hide;
    @XmlElement(name = "backgroundColor")
    private String backgroundColor;
    @XmlElement(name = "textColor")
    private String textColor;
    @XmlElement(name = "enabled")
    private boolean enabled;

    public ConditionalFormattingRule() {
        // Default constructor necessary for GWT serialisation.
    }

    private ConditionalFormattingRule(final String id,
                                      final ExpressionOperator expression,
                                      final boolean hide,
                                      final String backgroundColor,
                                      final String textColor,
                                      final boolean enabled) {
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
}
