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

package stroom.util;

import java.util.Objects;

/**
 * Set of ANSI colour codes and utility methods for brightening up the console
 */
public enum ConsoleColour {

    BLACK("\u001b[30m"),
    RED("\u001b[31m"),
    GREEN("\u001b[32m"),
    YELLOW("\u001b[33m"),
    BLUE("\u001b[34m"),
    MAGENTA("\u001b[35m"),
    CYAN("\u001b[36m"),
    WHITE("\u001b[37m"),
    RESET("\u001b[0m"),
    NO_COLOUR(null);

    private final String colourCode;

    ConsoleColour(final String colourCode) {
        this.colourCode = colourCode;
    }

    String getColourCode() {
        return colourCode;
    }

    public static String black(final String text) {
        return colourise(text, ConsoleColour.BLACK);
    }

    public static String red(final String text) {
        return colourise(text, ConsoleColour.RED);
    }

    public static String green(final String text) {
        return colourise(text, ConsoleColour.GREEN);
    }

    public static String yellow(final String text) {
        return colourise(text, ConsoleColour.YELLOW);
    }

    public static String blue(final String text) {
        return colourise(text, ConsoleColour.BLUE);
    }

    public static String magenta(final String text) {
        return colourise(text, ConsoleColour.MAGENTA);
    }

    public static String cyan(final String text) {
        return colourise(text, ConsoleColour.CYAN);
    }

    public static String white(final String text) {
        return colourise(text, ConsoleColour.WHITE);
    }

    public static String colourise(final String text, final ConsoleColour colour) {
        Objects.requireNonNull(text);
        Objects.requireNonNull(colour);
        if (colour.getColourCode() == null) {
            return text;
        } else {
            return colour.getColourCode() + text + ConsoleColour.RESET.colourCode;
        }
    }

    public static void appendBlack(final StringBuilder stringBuilder, final String text) {
        appendColour(stringBuilder, text, ConsoleColour.BLACK);
    }

    public static void appendRed(final StringBuilder stringBuilder, final String text) {
        appendColour(stringBuilder, text, ConsoleColour.RED);
    }

    public static void appendGreen(final StringBuilder stringBuilder, final String text) {
        appendColour(stringBuilder, text, ConsoleColour.GREEN);
    }

    public static void appendYellow(final StringBuilder stringBuilder, final String text) {
        appendColour(stringBuilder, text, ConsoleColour.YELLOW);
    }

    public static void appendBlue(final StringBuilder stringBuilder, final String text) {
        appendColour(stringBuilder, text, ConsoleColour.BLUE);
    }

    public static void appendMagenta(final StringBuilder stringBuilder, final String text) {
        appendColour(stringBuilder, text, ConsoleColour.MAGENTA);
    }

    public static void appendCyan(final StringBuilder stringBuilder, final String text) {
        appendColour(stringBuilder, text, ConsoleColour.CYAN);
    }

    public static void appendWhite(final StringBuilder stringBuilder, final String text) {
        appendColour(stringBuilder, text, ConsoleColour.WHITE);
    }

    private static void appendColour(final StringBuilder stringBuilder, final String text, final ConsoleColour colour) {
        Objects.requireNonNull(stringBuilder);
        Objects.requireNonNull(text);
        Objects.requireNonNull(colour);
        stringBuilder
                .append(colour.colourCode)
                .append(text)
                .append(ConsoleColour.RESET.colourCode);
    }

    public static ColouredStringBuilder colouredStringBuilder() {
        return new ColouredStringBuilder();
    }
}
