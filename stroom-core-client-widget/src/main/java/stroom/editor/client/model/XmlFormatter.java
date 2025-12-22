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

import com.google.gwt.regexp.shared.RegExp;

import java.util.Objects;

/**
 * A class for formatting XML. This class attempts to recognise XML
 * elements within text and then use this knowledge format the XML
 * structure.
 */
public class XmlFormatter {

    private static final String DOCTYPE_START = "<!DOCTYPE";
    private static final String EMPTY_ELEMENT_END = "/>";
    private static final String END_ELEMENT_START = "</";
    private static final String PI_START = "<?";
    private static final String COMMENT_START = "<!--";
    private static final String INDENT = "  ";

    // A pattern to match on pairs of elements or self-closing elements that indicate some data is xml.
    // As we may only have part of the document it might not be valid xml
    // XML element names can contain accented chars
    // https://regex101.com/r/5GR5Y5/2
    private static final RegExp XML_ELEMENT_PATTERN = RegExp.compile(
            "(?:<\\?xml [^>]*\\?>|<([A-Za-zŽžÀ-ÿ_][A-Za-zŽžÀ-ÿ0-9_.:-]*)(?:.|\\n)*?(?:\\/>|>(?:.|\\n)*?<\\/\\1))");

    private int depth;
    private ElementType elementType;
    private ElementType nextElementType;
    private ElementType lastElementType;
    private boolean preserveWhitespace;
    private boolean inAttValue;
    private boolean lastCharWasSpace;
    private CommentState commentState;
    private char[] arr;
    private int pos;
    private int elementStartPos;
    private boolean inEntity;
    private StringBuilder output;

    private void reset() {
        depth = 0;

        elementType = ElementType.CONTENT;
        nextElementType = null;
        lastElementType = null;
        preserveWhitespace = false;
        inAttValue = false;
        lastCharWasSpace = false;
        commentState = CommentState.START;

        inEntity = false;
        output = null;
    }

    /**
     * Processes some XML and attempts to format it depending on the
     * parameters supplied. The resulting string is HTML containing multiple
     * &lt;span&gt; elements to style the various XML fragments.
     *
     * @param input The input XML to format.
     * @return A formatted version of the input XML.
     */
    public String format(final String input) {
        // See if the data actually looks like xml before bothering to try to format it as such
        if (looksLikeXml(input)) {
            return doFormat(input);
        } else {
            return input;
        }
    }

    public String doFormat(final String input) {
        // Reset member variables.
        reset();

        boolean changedElement = false;
        boolean inElement = false;

        output = new StringBuilder(input.length() * 2);
        arr = input.toCharArray();
        for (pos = 0; pos < arr.length; pos++) {
            final char c = arr[pos];

            switch (c) {
                case '\n':
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

            // Output indentation if we are formatting this XML.
            if (changedElement && elementType != ElementType.CONTENT) {
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
//                outputStyle(applyStyle, output);

                // Format and output content.
                if (elementType != lastElementType) {
                    boolean dataElement = false;
                    // If the last element was a start element and the next
                    // element is an end element then this is a data element.
                    if (lastElementType == ElementType.START) {
                        if (nextElementType == null) {
                            nextElementType = getElementType(pos);
                        }
                        if (nextElementType != null && nextElementType == ElementType.END) {
                            dataElement = true;
                        }
                    }

                    if (dataElement) {
                        // Continue to preserve whitespace if this is a data element.
                        preserveWhitespace = true;
                        escape(c);
                        lastElementType = elementType;

                    } else if (isWhitespace(c)) {
                        // Ignore this character...
                        // This has the effect of trimming all of the whitespace
                        // off the beginning of some content as we won't set the
                        // lastElementType until we start to get some non
                        // whitespace chars.

                    } else {
                        escape(c);
                        lastElementType = elementType;
                    }
                } else {
                    escape(c);
                }
            } else {
                // Trim some space off the preceding content.
                if (!preserveWhitespace && lastElementType == ElementType.CONTENT) {
                    trimEndWhitespace(output);
                }

                // Set these variables to stop all whitespace being preserved.
                nextElementType = null;
                preserveWhitespace = false;

//                outputStyle(applyStyle, output);
                if (elementType != ElementType.CONTENT && elementType != ElementType.COMMENT && c == '"') {
                    // Toggle attribute value flag.
                    inAttValue = !inAttValue;
                }

                // Do not format attribute values.
                if (inAttValue) {
                    escape(c);
                    lastCharWasSpace = false;

                } else {
                    // Output the character.
                    if (c == '>') {
                        // Trim spare chars off the end of elements.
                        trimEndWhitespace(output);
                        escape(c);
                        lastCharWasSpace = false;

                    } else if (elementType == ElementType.EMPTY && !lastCharWasSpace && c == '/') {
                        // Insert a space before the end of an empty element.
                        escape(' ');
                        escape(c);
                        lastCharWasSpace = false;

                    } else if (isWhitespace(c)) {
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

        return output.toString();
    }

    public static boolean looksLikeXml(final String data) {
        // Essentially we are looking for the xml declaration, a pair of opening/closing elements
        // or a self-closing element
        // This is to try and stop it formatting non-xml data as xml when it finds angle brackets in the data
        return XML_ELEMENT_PATTERN.test(data);
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
            if (c == '"' && !startsWith(sb, COMMENT_START)) {
                // Toggle the fact that we are now either in or out of an
                // attribute value.
                inAttValue = !inAttValue;

            } else if (!inAttValue && c == '>') {
                if (startsWith(sb, COMMENT_START)) {
                    elementType = ElementType.COMMENT;
                } else if (startsWith(sb, PI_START)) {
                    elementType = ElementType.PI;
                } else if (startsWith(sb, END_ELEMENT_START)) {
                    elementType = ElementType.END;
                } else if (endsWith(sb, EMPTY_ELEMENT_END)) {
                    elementType = ElementType.EMPTY;
                } else if (startsWith(sb, DOCTYPE_START)) {
                    elementType = ElementType.DOCTYPE;
                }

                if (elementType == ElementType.COMMENT) {
                    // Don't break out of a comment unless --> is found.
                    if (endsWith(sb, "-->")) {
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

    private boolean startsWith(final StringBuilder stringBuilder, final String prefix) {
        Objects.requireNonNull(stringBuilder);
        Objects.requireNonNull(prefix);
        return stringBuilder.indexOf(prefix) == 0;
    }

    private boolean endsWith(final StringBuilder stringBuilder, final String suffix) {
        Objects.requireNonNull(stringBuilder);
        Objects.requireNonNull(suffix);
        final int expectedSuffixPos = stringBuilder.length() - suffix.length();
        return stringBuilder.indexOf(suffix) == expectedSuffixPos;
    }

    private void write(final String string) {
        output.append(string);
    }

    private void escape(final char c) {
//        switch (c) {
//            case '\r':
//                // Ignore carriage returns.
//                break;
//            case '<':
//                output.append(LT);
//                break;
//            case '>':
//                // Convert < to &gt;.
//                output.append(GT);
//                break;
//            case '&':
//                // Convert & to &amp;.
//                output.append(AMP);
//                break;
//            default:
        output.append(c);
//        }
    }

    /**
     * The different fragment types of an XML instance.
     */
    private enum ElementType {
        DOCTYPE,
        START,
        END,
        EMPTY,
        PI,
        COMMENT,
        CONTENT
    }

    private enum CommentState {
        START,
        CONTENT,
        END
    }
}
