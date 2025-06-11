package stroom.query.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "light",
        "dark"})
@JsonInclude(Include.NON_NULL)
public class CustomConditionalFormattingStyle {

    @JsonProperty("light")
    private final CustomRowStyle light;
    @JsonProperty("dark")
    private final CustomRowStyle dark;

    @JsonCreator
    public CustomConditionalFormattingStyle(@JsonProperty("light") final CustomRowStyle light,
                                            @JsonProperty("dark") final CustomRowStyle dark) {
        this.light = light;
        this.dark = dark;
    }

    public CustomRowStyle getLight() {
        return light;
    }

    public CustomRowStyle getDark() {
        return dark;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    public static final class Builder {

        private CustomRowStyle light;
        private CustomRowStyle dark;


        private Builder() {
        }

        private Builder(final CustomConditionalFormattingStyle style) {
            this.light = style.light;
            this.dark = style.dark;
        }

        public Builder light(final CustomRowStyle light) {
            this.light = light;
            return this;
        }

        public Builder dark(final CustomRowStyle dark) {
            this.dark = dark;
            return this;
        }

        public CustomConditionalFormattingStyle build() {
            return new CustomConditionalFormattingStyle(light, dark);
        }
    }
}
