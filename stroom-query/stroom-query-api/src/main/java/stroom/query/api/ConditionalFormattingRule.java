/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api;

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
        "enabled",
        "formattingType",
        "formattingStyle",
        "customStyle",
        "textAttributes"
})
@JsonInclude(Include.NON_NULL)
public class ConditionalFormattingRule {

    @JsonProperty("id")
    private final String id;
    @JsonProperty("expression")
    private final ExpressionOperator expression;
    @JsonProperty("hide")
    private final boolean hide;
    @JsonProperty("backgroundColor")
    @Deprecated // moved to CustomConditionalFormattingStyle kept for serialisation backward compatibility.
    private final String backgroundColor;
    @JsonProperty("textColor")
    @Deprecated // moved to CustomConditionalFormattingStyle kept for serialisation backward compatibility.
    private final String textColor;
    @JsonProperty("enabled")
    private final boolean enabled;

    @JsonProperty("formattingType")
    private final ConditionalFormattingType formattingType;
    @JsonProperty("formattingStyle")
    private final ConditionalFormattingStyle formattingStyle;
    @JsonProperty("customStyle")
    private final CustomConditionalFormattingStyle customStyle;
    @JsonProperty("textAttributes")
    private final TextAttributes textAttributes;

    @JsonCreator
    public ConditionalFormattingRule(@JsonProperty("id") final String id,
                                     @JsonProperty("expression") final ExpressionOperator expression,
                                     @JsonProperty("hide") final boolean hide,
                                     @Deprecated @JsonProperty("backgroundColor") final String backgroundColor,
                                     @Deprecated @JsonProperty("textColor") final String textColor,
                                     @JsonProperty("enabled") final boolean enabled,

                                     @JsonProperty("formattingType") final ConditionalFormattingType formattingType,
                                     @JsonProperty("formattingStyle") final ConditionalFormattingStyle formattingStyle,
                                     @JsonProperty("customStyle") final CustomConditionalFormattingStyle customStyle,
                                     @JsonProperty("textAttributes") final TextAttributes textAttributes) {
        this.id = id;
        this.expression = expression;
        this.hide = hide;
        this.backgroundColor = null;
        this.textColor = null;
        this.enabled = enabled;
        this.formattingType = formattingType;
        this.formattingStyle = formattingStyle;
        this.textAttributes = textAttributes;

        if (customStyle == null && (backgroundColor != null && textColor != null)) {
            final CustomRowStyle customRowStyle = CustomRowStyle
                    .builder()
                    .backgroundColour(backgroundColor)
                    .textColour(textColor)
                    .build();
            this.customStyle = CustomConditionalFormattingStyle
                    .builder()
                    .light(customRowStyle)
                    .dark(customRowStyle)
                    .build();
        } else {
            this.customStyle = customStyle;
        }
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

    public boolean isEnabled() {
        return enabled;
    }

    public ConditionalFormattingType getFormattingType() {
        return formattingType;
    }

    public ConditionalFormattingStyle getFormattingStyle() {
        return formattingStyle;
    }

    public CustomConditionalFormattingStyle getCustomStyle() {
        return customStyle;
    }

    public TextAttributes getTextAttributes() {
        return textAttributes;
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
               Objects.equals(formattingType, that.formattingType) &&
               Objects.equals(formattingStyle, that.formattingStyle) &&
               Objects.equals(customStyle, that.customStyle) &&
               Objects.equals(textAttributes, that.textAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                expression,
                hide,
                enabled,
                formattingType,
                formattingStyle,
                customStyle,
                textAttributes);
    }

    @Override
    public String toString() {
        return "ConditionalFormattingRule{" +
               "id='" + id + '\'' +
               ", expression=" + expression +
               ", hide=" + hide +
               ", enabled=" + enabled +
               ", formattingType=" + formattingType +
               ", formattingStyle=" + formattingStyle +
               ", customStyle=" + customStyle +
               ", textAttributes=" + textAttributes +
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
        private boolean enabled;
        private ConditionalFormattingType formattingType;
        private ConditionalFormattingStyle formattingStyle;
        private CustomConditionalFormattingStyle customStyle;
        private TextAttributes textAttributes;

        private Builder() {
        }

        private Builder(final ConditionalFormattingRule rule) {
            this.id = rule.id;
            this.expression = rule.expression;
            this.hide = rule.hide;
            this.enabled = rule.enabled;
            this.formattingType = rule.formattingType;
            this.formattingStyle = rule.formattingStyle;
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

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder formattingType(final ConditionalFormattingType formattingType) {
            this.formattingType = formattingType;
            return this;
        }

        public Builder formattingStyle(final ConditionalFormattingStyle formattingStyle) {
            this.formattingStyle = formattingStyle;
            return this;
        }

        public Builder customStyle(final CustomConditionalFormattingStyle customStyle) {
            this.customStyle = customStyle;
            return this;
        }

        public Builder textAttributes(final TextAttributes textAttributes) {
            this.textAttributes = textAttributes;
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
                    null,
                    null,
                    enabled,
                    formattingType,
                    formattingStyle,
                    customStyle,
                    textAttributes);
        }
    }
}
