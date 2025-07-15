package stroom.query.api.v2;

import stroom.docref.HasDisplayValue;

import java.util.ArrayList;
import java.util.List;

public enum ConditionalFormattingStyle implements HasDisplayValue {
    NONE("None", ""),
    RED("Red", "cf-red"),
    PINK("Pink", "cf-pink"),
    PURPLE("Purple", "cf-purple"),
    DEEP_PURPLE("Deep Purple", "cf-deep-purple"),
    INDIGO("Indigo", "cf-indigo"),
    BLUE("Blue", "cf-blue"),
    LIGHT_BLUE("Light Blue", "cf-light-blue"),
    CYAN("Cyan", "cf-cyan"),
    TEAL("Teal", "cf-teal"),
    GREEN("Green", "cf-green"),
    LIGHT_GREEN("Light Green", "cf-light-green"),
    LIME("Lime", "cf-lime"),
    YELLOW("Yellow", "cf-yellow"),
    AMBER("Amber", "cf-amber"),
    ORANGE("Orange", "cf-orange"),
    DEEP_ORANGE("Deep Orange", "cf-deep-orange"),
    BROWN("Brown", "cf-brown"),
    GREY("Grey", "cf-grey"),
    BLUE_GREY("Blue Grey", "cf-blue-grey"),
    ;

    public static final List<ConditionalFormattingStyle> LIST = new ArrayList<>();

    static {
        LIST.add(NONE);
        LIST.add(RED);
        LIST.add(PINK);
        LIST.add(PURPLE);
        LIST.add(DEEP_PURPLE);
        LIST.add(INDIGO);
        LIST.add(BLUE);
        LIST.add(LIGHT_BLUE);
        LIST.add(CYAN);
        LIST.add(TEAL);
        LIST.add(GREEN);
        LIST.add(LIGHT_GREEN);
        LIST.add(LIME);
        LIST.add(YELLOW);
        LIST.add(AMBER);
        LIST.add(ORANGE);
        LIST.add(DEEP_ORANGE);
        LIST.add(BROWN);
        LIST.add(GREY);
        LIST.add(BLUE_GREY);
    }

    private final String displayValue;
    private final String cssClassName;

    ConditionalFormattingStyle(final String displayValue, final String cssClassName) {
        this.displayValue = displayValue;
        this.cssClassName = cssClassName;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getCssClassName() {
        return cssClassName;
    }
}
