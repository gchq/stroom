package stroom.util;

import java.util.Objects;

/**
 * Wraps a StringBuilder and provides methods for appending coloured
 * text for use on the console
 */
public class ColouredStringBuilder {

    private final StringBuilder stringBuilder = new StringBuilder();

    public ColouredStringBuilder appendBlack(final String text) {
        return append(text, ConsoleColour.BLACK);
    }

    public ColouredStringBuilder appendRed(final String text) {
        return append(text, ConsoleColour.RED);
    }

    public ColouredStringBuilder appendGreen(final String text) {
        return append(text, ConsoleColour.GREEN);
    }

    public ColouredStringBuilder appendYellow(final String text) {
        return append(text, ConsoleColour.YELLOW);
    }

    public ColouredStringBuilder appendBlue(final String text) {
        return append(text, ConsoleColour.BLUE);
    }

    public ColouredStringBuilder appendMagenta(final String text) {
        return append(text, ConsoleColour.MAGENTA);
    }

    public ColouredStringBuilder appendCyan(final String text) {
        return append(text, ConsoleColour.CYAN);
    }

    public ColouredStringBuilder appendWhite(final String text) {
        return append(text, ConsoleColour.WHITE);
    }

    public ColouredStringBuilder append(final String str) {
        stringBuilder.append(str);
        return this;
    }

    @Override
    public String toString() {
        return stringBuilder.toString();
    }

    public ColouredStringBuilder append(final String text, final ConsoleColour colour) {
        Objects.requireNonNull(text);
        Objects.requireNonNull(colour);
        stringBuilder
            .append(colour.getColourCode())
            .append(text)
            .append(ConsoleColour.RESET.getColourCode());
        return this;
    }
}
