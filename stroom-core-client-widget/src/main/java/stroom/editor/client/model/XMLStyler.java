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

package stroom.editor.client.model;

import stroom.util.shared.TextRange;

import java.util.ArrayList;
import java.util.List;

/**
 * A class for styling and formatting XML. This class attempts to recognise XML
 * elements within text and then use this knowledge to style and format the XML
 * structure.
 */
public class XMLStyler {

    private static final String DOCTYPE_START = "<!DOCTYPE";
    private static final String EMPTY_ELEMENT_END = "/>";
    private static final String END_ELEMENT_START = "</";
    private static final String PI_START = "<?";
    private static final String COMMENT_START = "<!--";
    private static final String INDENT = "  ";
    private static final String GT = "&gt;";
    private static final String LT = "&lt;";
    private static final String AMP = "&amp;";
    private boolean inHighlight;
    private boolean changedHighlight;
    private int depth;
    private ElementType elementType;
    private ElementType nextElementType;
    private ElementType lastElementType;
    private Style lastStyle;
    private boolean doneElementStyle;
    private boolean preserveWhitespace;
    private boolean inAttValue;
    private boolean lastCharWasSpace;
    private CommentState commentState;
    private char c;
    private char[] arr;
    private int pos;
    private int elementStartPos;
    private boolean inEntity;
    private StringBuilder output;

    private void reset() {
        inHighlight = false;
        changedHighlight = false;

        depth = 0;

        elementType = ElementType.CONTENT;
        nextElementType = null;
        lastElementType = null;
        lastStyle = null;
        doneElementStyle = false;
        preserveWhitespace = false;
        inAttValue = false;
        lastCharWasSpace = false;
        commentState = CommentState.START;

        inEntity = false;
        output = null;
    }

    /**
     * Processes some XML and attempts to style and format it depending on the
     * parameters supplied. The resulting string is HTML containing multiple
     * &lt;span&gt; elements to style the various XML fragments.
     *
     * @param input      The input XML to style and format.
     * @param applyStyle True if this method should add styles to the resultant HTML.
     * @param format     True if this method should format the XML.
     * @return An HTML string of the formatted and styled XML.
     */
    public String processXML(final String input, final boolean applyStyle, final boolean format, final int startLineNo,
                             final List<TextRange> highlights) {
        List<TextRange> remainingHighlights = null;
        if (highlights != null) {
            remainingHighlights = new ArrayList<>(highlights);
        }

        // Reset member variables.
        reset();

        int lineNo = startLineNo;
        int colNo = 0;

        boolean changedElement = false;
        boolean inElement = false;

        output = new StringBuilder(input.length() * 2);
        arr = input.toCharArray();
        for (pos = 0; pos < arr.length; pos++) {
            c = arr[pos];
            colNo++;

            switch (c) {
                case '\n':
                    lineNo++;
                    colNo = 0;

                    // Make sure the element type is content if we aren't in an
                    // element.
                    if (!inElement) {
                        elementType = ElementType.CONTENT;
                    }
                    break;
                case '<':
                    if (!inAttValue) {
                        if (!inElement) {
                            // We are entering an element so reset some variables.
                            changedElement = true;
                            inElement = true;
                            inAttValue = false;
                            doneElementStyle = false;
                            lastCharWasSpace = false;

                            // Get the element type we are entering.
                            elementType = getElementType(pos);
                            elementStartPos = pos;
                            if (elementType == ElementType.COMMENT) {
                                commentState = CommentState.START;
                            }
                        } else if (elementType == ElementType.DOCTYPE) {
                            inEntity = true;
                        }
                    }
                    break;
                case '>':
                    if (!inAttValue) {
                        if (inEntity) {
                            inEntity = false;
                        } else if (inElement) {
                            // If we are in a comment make sure this is the proper
                            // end to a comment.
                            if (elementType == ElementType.COMMENT) {
                                if (back(2) == '-' && back(1) == '-') {
                                    inElement = false;
                                }
                            } else {
                                inElement = false;
                            }
                        }
                    }
                    break;
                default:
                    // Make sure the element type is content if we aren't in an
                    // element.
                    if (!inElement) {
                        elementType = ElementType.CONTENT;
                    }

                    // Do some processing to figure out what the state should be if
                    // we are in a comment.
                    if (elementType == ElementType.COMMENT) {
                        if (commentState == CommentState.START && pos > elementStartPos + 3) {
                            commentState = CommentState.CONTENT;
                        }
                        if (commentState == CommentState.CONTENT
                                && c == '-'
                                && forward(1) == '-'
                                && forward(2) == '>') {
                            commentState = CommentState.END;
                        }
                    }

                    break;
            }

            // See if we want to toggle highlighting.
            if (remainingHighlights != null && remainingHighlights.size() > 0) {
                if (!inHighlight) {
                    // We assume highlights are in appearance order so get the
                    // first from the list.
                    final TextRange highlight = remainingHighlights.get(0);
                    if ((lineNo == highlight.getFrom().getLineNo() && colNo >= highlight.getFrom().getColNo())
                            || lineNo > highlight.getFrom().getLineNo()) {
                        inHighlight = true;
                        changedHighlight = true;
                    }
                } else {
                    // We assume highlights are in appearance order so get the
                    // first from the list.
                    final TextRange highlight = remainingHighlights.get(0);
                    if ((lineNo == highlight.getTo().getLineNo() && colNo >= highlight.getTo().getColNo())
                            || lineNo > highlight.getTo().getLineNo()) {
                        inHighlight = false;
                        changedHighlight = true;

                        // We have finished adding this highlight so remove it
                        // from the list.
                        remainingHighlights.remove(0);
                    }
                }
            }

            // Output indentation if we are formatting this XML.
            if (format && changedElement && elementType != ElementType.CONTENT) {
                if (elementType == ElementType.END) {
                    depth--;
                }

                // If we are looking at a start element and we are already in a
                // start element then output a new line.
                if (lastElementType != null && ((lastElementType != ElementType.END && elementType != ElementType.END)
                        || lastElementType == ElementType.END || lastElementType == ElementType.EMPTY
                        || (lastElementType == ElementType.COMMENT && elementType == ElementType.END))) {
                    escape('\n');

                    // Output an extra new line before comments.
                    if (elementType == ElementType.COMMENT) {
                        escape('\n');
                    }

                    for (int i = 0; i < depth; i++) {
                        write(INDENT);
                    }
                }

                if (elementType == ElementType.START) {
                    depth++;
                }
            }

            // Now format and output the character.
            if (elementType == ElementType.CONTENT) {
                outputStyle(applyStyle, output);

                // Format and output content.
                if (elementType != lastElementType) {
                    if (format && isWhitespace(c) && !preserveWhitespace) {
                        // Ignore this character...
                        // This has the effect of trimming all of the whitespace
                        // off the beginning of some content as we won't set the
                        // lastElementType until we start to get some non
                        // whitespace chars.

                        // If the last element was a start element and the next
                        // element is an end element then we want to preserve
                        // whitespace.
                        if (lastElementType == ElementType.START) {
                            if (nextElementType == null) {
                                nextElementType = getElementType(pos);
                            }

                            if (nextElementType != null && nextElementType == ElementType.END) {
                                preserveWhitespace = true;
                                escape(c);
                                lastElementType = elementType;
                            }
                        }

                    } else {
                        escape(c);
                        lastElementType = elementType;
                    }
                } else {
                    escape(c);
                }
            } else {
                // Trim some space off the preceding content.
                if (format && !preserveWhitespace && lastElementType == ElementType.CONTENT) {
                    trimEndWhitespace(output);
                }

                // Set these variables to stop all whitespace being preserved.
                nextElementType = null;
                preserveWhitespace = false;

                outputStyle(applyStyle, output);

                // Do not format attribute values.
                if (inAttValue) {
                    escape(c);
                    lastCharWasSpace = false;

                } else {
                    // Output the character.
                    if (c == '>') {
                        // Trim spare chars off the end of elements.
                        if (format) {
                            trimEndWhitespace(output);
                        }
                        escape(c);
                        lastCharWasSpace = false;

                    } else if (format && elementType == ElementType.EMPTY && !lastCharWasSpace && c == '/') {
                        // Insert a space before the end of an empty element.
                        escape(' ');
                        escape(c);
                        lastCharWasSpace = false;

                    } else if (format && isWhitespace(c)) {
                        if (elementType == ElementType.COMMENT) {
                            // If this is a new line then output it and add the
                            // necessary indent.
                            if (c == '\n') {
                                escape(c);
                                for (int i = 0; i < depth; i++) {
                                    write(INDENT);
                                }
                            } else if (!lastCharWasSpace) {
                                escape(' ');
                            }
                            lastCharWasSpace = true;

                        } else if (!lastCharWasSpace && forward(1) != '>' && forward(1) != '/' && forward(1) != '='
                                && back(1) != '=') {
                            // Only allow a single space to be added between
                            // attributes.
                            escape(' ');
                            lastCharWasSpace = true;
                        }

                    } else {
                        escape(c);
                        lastCharWasSpace = false;
                    }
                }

                lastElementType = elementType;
            }

            changedElement = false;
        }

        // Close the highlight element if we are in one.
        if (inHighlight) {
            write(Style.HIGHLIGHT.getEnd());
        }

        // Close the last style element if there is one.
        if (lastStyle != null) {
            write(lastStyle.getEnd());
        }

        return output.toString();
    }

    /**
     * Determines the type of element that we are entering into.
     *
     * @return The type of element that we have found.
     */
    private ElementType getElementType(final int pos) {
        ElementType elementType = ElementType.START;
        final StringBuilder sb = new StringBuilder();

        // Find the start bracket.
        int start = -1;
        for (int i = pos; i < arr.length && start == -1; i++) {
            final char c = arr[i];
            if (c == '<') {
                start = i;
            }
        }

        // Find the element type.
        boolean inAttValue = false;
        for (int i = start; i < arr.length; i++) {
            final char c = arr[i];

            if (!isWhitespace(c)) {
                sb.append(c);
            }

            // Test the element type when we think we are at the end of the
            // element.
            if (c == '"') {
                // Toggle the fact that we are now either in or out of an
                // attribute value.
                inAttValue = !inAttValue;

            } else if (!inAttValue && c == '>') {
                final String element = sb.toString();
                if (element.startsWith(COMMENT_START)) {
                    elementType = ElementType.COMMENT;
                } else if (element.startsWith(PI_START)) {
                    elementType = ElementType.PI;
                } else if (element.startsWith(END_ELEMENT_START)) {
                    elementType = ElementType.END;
                } else if (element.endsWith(EMPTY_ELEMENT_END)) {
                    elementType = ElementType.EMPTY;
                } else if (element.startsWith(DOCTYPE_START)) {
                    elementType = ElementType.DOCTYPE;
                }

                if (elementType == ElementType.COMMENT) {
                    // Don't break out of a comment unless --> is found.
                    if (element.endsWith("-->")) {
                        return elementType;
                    }
                } else {
                    return elementType;
                }
            }
        }

        return elementType;
    }

    /**
     * Returns the char that is the specified number of chars before the current
     * array position ignoring whitespace chars.
     *
     * @param len The number of characters before the current array position.
     * @return The char at the specified location or '~' if the array bounds are
     * exceeded as we know that '~' isn't a character this is tested
     * for.
     */
    private char back(final int len) {
        int index = pos - len;
        while (index >= 0) {
            final char c = arr[index];
            if (isWhitespace(c)) {
                index--;
            } else {
                return c;
            }
        }

        // Return a character that I know we aren't looking for.
        return '~';
    }

    /**
     * Returns the char that is the specified number of chars after the current
     * array position ignoring whitespace chars.
     *
     * @param len The number of characters after the current array position.
     * @return The char at the specified location or '~' if the array bounds are
     * exceeded as we know that '~' isn't a character this is tested
     * for.
     */
    private char forward(final int len) {
        int index = pos + len;
        while (index < arr.length) {
            final char c = arr[index];
            if (isWhitespace(c)) {
                index++;
            } else {
                return c;
            }
        }

        // Return a character that I know we aren't looking for.
        return '~';
    }

    /**
     * Outputs style span elements based on the current element type and whether
     * we are currently in a highlighted section or not.
     *
     * @param applyStyle Determines if element styles are to be output. This does not
     *                   affect the output of highlight styles as they are applied to
     *                   styled and non styled text.
     * @param output     The output <code>StringBuilder</code> to append the style span
     *                   elements to.
     */
    private void outputStyle(final boolean applyStyle, final StringBuilder output) {
        // Output the start style element if the style has changed.
        if (applyStyle) {
            final Style style = getStyle(c, elementType);

            // If the style has changed we need to output the new style.
            if (style != lastStyle) {
                // We need to make sure that elements don't overlap so we need
                // to close the highlight element if it is open.
                if (inHighlight) {
                    if (!changedHighlight) {
                        // If we haven't only just switched into a highlight
                        // section then we must already be in one. In this case
                        // we need to close the previous highlight section.
                        write(Style.HIGHLIGHT.getEnd());
                    }
                } else if (changedHighlight) {
                    // We are moving out of the highlight state so output an end
                    // span.
                    write(Style.HIGHLIGHT.getEnd());
                }

                if (lastStyle != null) {
                    // Close the previous style.
                    write(lastStyle.getEnd());
                }
                // Output the appropriate style.
                write(style.getStart());

                if (inHighlight) {
                    // Output a highlight if we are in a highlight section.
                    write(Style.HIGHLIGHT.getStart());
                }
            } else {
                // This section will only be executed for the first char as all
                // subsequent calls to this method will have set the lastStyle.
                if (lastStyle == null) {
                    // Output the appropriate style.
                    write(style.getStart());
                }

                // Output the highlight style if the output is to be highlighted
                // from the start.
                outputHighlight(output);
            }

            // Record the style that was applied.
            lastStyle = style;

        } else {
            // Output a highlight element start/end if we are moving in/out of a
            // highlight section.
            outputHighlight(output);
        }

        // Set the flag to indicate highlighting hasn't just been turned on/off
        changedHighlight = false;
    }

    /**
     * Outputs the appropriate highlight start or end element depending on
     * whether we are switching to or from the highlight state.
     *
     * @param output The output <code>StringBuilder</code> to append the highlight
     *               element to.
     */
    private void outputHighlight(final StringBuilder output) {
        if (changedHighlight) {
            if (inHighlight) {
                // Output a highlight if we are in a highlight section.
                write(Style.HIGHLIGHT.getStart());
            } else {
                // We are moving out of the highlight state so output an end
                // span.
                write(Style.HIGHLIGHT.getEnd());
            }
        }
    }

    /**
     * Gets the style type that is appropriate for the current char in the
     * current element type.
     *
     * @param c           The current char.
     * @param elementType The current element type.
     * @return The appropriate style.
     */
    private Style getStyle(final char c, final ElementType elementType) {
        Style style = elementType.getStyle();

        if (elementType != ElementType.CONTENT) {
            if (elementType == ElementType.PI || elementType == ElementType.DOCTYPE) {
                if (c == '"') {
                    // Toggle attribute value flag.
                    inAttValue = !inAttValue;

                } else if (!inAttValue
                        && (c == '?' || c == '!' || c == '/' || c == ':' || c == '=' || c == '<' || c == '>')) {
                    style = Style.SYNTAX;
                }

            } else if (elementType == ElementType.COMMENT) {
                if (commentState == CommentState.START || commentState == CommentState.END) {
                    style = Style.SYNTAX;
                }

            } else {
                if (c == '"') {
                    // Toggle attribute value flag.
                    inAttValue = !inAttValue;
                    style = Style.ATTRIBUTE_VALUE;

                } else if (inAttValue) {
                    style = Style.ATTRIBUTE_VALUE;

                } else if (c == '/' || c == ':' || c == '=' || c == '<' || c == '>') {
                    style = Style.SYNTAX;

                } else {
                    if (!doneElementStyle && isWhitespace(c)) {
                        doneElementStyle = true;
                    }

                    if (doneElementStyle) {
                        style = Style.ATTRIBUTE_NAME;
                    }
                }
            }
        }

        return style;
    }

    /**
     * Tests if a character is a whitespace character.
     *
     * @param c The character to test.
     * @return True if the character is a space, tab, new line or carriage
     * return.
     */
    private boolean isWhitespace(final char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /**
     * Reduces the current length of a <code>StringBuilder</code> to trim any
     * whitespace off the end of the buffer.
     *
     * @param sb The <code>StringBuilder</code> to trim.
     */
    private void trimEndWhitespace(final StringBuilder sb) {
        int index = sb.length() - 1;
        while (index >= 0 && isWhitespace(sb.charAt(index))) {
            index--;
        }
        sb.setLength(index + 1);
    }

    private void write(final String string) {
        output.append(string);
    }

    private void escape(final char c) {
        switch (c) {
            case '\r':
                // Ignore carriage returns.
                break;
            case '<':
                output.append(LT);
                break;
            case '>':
                // Convert < to &gt;.
                output.append(GT);
                break;
            case '&':
                // Convert & to &amp;.
                output.append(AMP);
                break;
            default:
                output.append(c);
        }
    }

    /**
     * Individual styles for each XML fragment type.
     */
    public enum Style {
        SYNTAX("s"),
        PI("pi"),
        DOCTYPE("d"),
        ELEMENT_NAME("e"),
        ATTRIBUTE_NAME("an"),
        ATTRIBUTE_VALUE("av"),
        CONTENT(
                "c"),
        COMMENT("z"),
        HIGHLIGHT("hl");

        private final String start;
        private final String end;

        Style(final String style) {
            this.start = "<" + style + ">";
            this.end = "</" + style + ">";
        }

        public String getStart() {
            return start;
        }

        public String getEnd() {
            return end;
        }

        @Override
        public String toString() {
            return start;
        }
    }

    /**
     * The different fragment types of an XML instance.
     */
    private enum ElementType {
        DOCTYPE(Style.DOCTYPE),
        START(Style.ELEMENT_NAME),
        END(Style.ELEMENT_NAME),
        EMPTY(Style.ELEMENT_NAME),
        PI(
                Style.PI),
        COMMENT(Style.COMMENT),
        CONTENT(Style.CONTENT);

        private final Style style;

        ElementType(final Style style) {
            this.style = style;
        }

        public Style getStyle() {
            return style;
        }
    }

    private enum CommentState {
        START,
        CONTENT,
        END
    }
}
