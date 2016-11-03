/*
 * Copyright 2016 Crown Copyright
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

package stroom.xmleditor.client.model;

/**
 * A class for styling and formatting XML. This class attempts to recognise XML
 * elements within text and then use this knowledge to style and format the XML
 * structure.
 */
public class OldXMLStyler {
    private static final String WHITESPACE = "\\s";
    private static final String START_COMMENT = "<!--";
    private static final String END_COMMENT = "-->";
    private static final String INDENT = "  ";
    private static final String END_SPAN = "</span>";
    private static final String GT = "&gt;";
    private static final String LT = "&lt;";

    /**
     * Individual styles for each XML fragment type.
     */
    private enum Style {
        SYNTAX("xsSyntax"), PI("xsPI"), DOCTYPE("xsDocType"), ELEMENT_NAME("xsElName"), ATTRIBUTE_NAME(
                "xsAttName"), ATTRIBUTE_VALUE("xsAttValue"), CONTENT("xsContent"), COMMENT("xsComment"), HIGHLIGHT(
                        "xsHighlight");

        private final String html;

        Style(final String style) {
            this.html = "<span class=\"" + style + "\">";
        }

        public String getHTML() {
            return html;
        }
    }

    /**
     * The different fragment types of an XML instance.
     */
    private enum ElementType {
        DOCTYPE(Style.DOCTYPE), START(Style.ELEMENT_NAME), END(Style.ELEMENT_NAME), EMPTY(Style.ELEMENT_NAME), PI(
                Style.PI), COMMENT(Style.COMMENT);

        private final Style style;

        ElementType(final Style style) {
            this.style = style;
        }

        public Style getStyle() {
            return style;
        }
    }

    /**
     * Processes some XML and attempts to style and format it depending on the
     * parameters supplied. The resulting string is HTML containing multiple
     * &lt;span&gt; elements to style the various XML fragments.
     *
     * @param input
     *            The input XML to style and format.
     * @param style
     *            True if this method should add styles to the resultant HTML.
     * @param format
     *            True if this method should format the XML.
     * @return An HTML string of the formatted and styled XML.
     */
    public static String processXML(final String input, final boolean style, final boolean format) {
        final StringBuilder output = new StringBuilder();

        int start = 0;
        int end = 0;
        boolean inElement = false;
        String frag = null;
        int depth = 0;

        ElementType elementType = null;
        ElementType lastElementType = null;

        while (start != -1) {
            if (!inElement) {
                // We are in content.
                end = input.indexOf('<', start);
                if (end == -1) {
                    frag = input.substring(start);
                } else {
                    frag = input.substring(start, end);
                }

                processContent(frag, output, style, format);

            } else {
                // We are in an element.
                end = input.indexOf('>', start);
                if (end == -1) {
                    frag = input.substring(start);
                } else {
                    end++;
                    frag = input.substring(start, end);
                }

                final String tmp = frag.replaceAll(WHITESPACE, "");

                if (tmp.length() >= 3) {
                    // Determine the type of this element.
                    if (tmp.charAt(1) == '/') {
                        elementType = ElementType.END;
                    } else if (tmp.charAt(1) == '?') {
                        elementType = ElementType.PI;
                    } else if (tmp.charAt(1) == '!') {
                        if (tmp.length() >= 4 && tmp.startsWith(START_COMMENT)) {
                            elementType = ElementType.COMMENT;

                            // Re-scope the fragment to make sure it includes
                            // all of the comment.
                            end = input.indexOf(END_COMMENT, start);
                            if (end == -1) {
                                frag = input.substring(start);
                            } else {
                                end += END_COMMENT.length();
                                frag = input.substring(start, end);
                            }

                        } else {
                            elementType = ElementType.DOCTYPE;
                        }
                    } else if (tmp.charAt(tmp.length() - 2) == '/') {
                        elementType = ElementType.EMPTY;
                    } else {
                        elementType = ElementType.START;
                    }
                }

                if (elementType == ElementType.END) {
                    depth--;
                }

                // If we are looking at a start element and we are already
                // in a start element then output a new line.
                if (format && lastElementType != null
                        && ((lastElementType != ElementType.END && elementType != null
                                && elementType != ElementType.END) || lastElementType == ElementType.END
                                || lastElementType == ElementType.EMPTY)) {
                    output.append('\n');

                    // Output an extra new line before comments.
                    if (elementType != null && elementType == ElementType.COMMENT) {
                        output.append('\n');
                    }

                    for (int i = 0; i < depth; i++) {
                        output.append(INDENT);
                    }
                }

                if (elementType == ElementType.START) {
                    depth++;
                }

                processElement(frag, output, elementType, style, format);

                lastElementType = elementType;
            }

            start = end;
            inElement = !inElement;

        }

        return output.toString();
    }

    /**
     * Processes an XML content fragment.
     *
     * @param frag
     *            The fragment to process.
     * @param output
     *            The output buffer.
     * @param style
     *            True if we want to apply style to the fragment.
     * @param format
     *            True if we want to format the fragment.
     */
    private static void processContent(final String frag, final StringBuilder output, final boolean style,
            final boolean format) {
        String content = frag;

        if (format) {
            content = content.trim();
        }

        if (style && content.length() > 0) {
            output.append(Style.CONTENT.getHTML());
            output.append(content);
            output.append(END_SPAN);

        } else {
            output.append(content);
        }
    }

    /**
     * Processes an XML element fragment.
     *
     * @param frag
     *            The fragment to process.
     * @param output
     *            The output buffer.
     * @param elementType
     *            The type of element that we are trying to format and style.
     * @param style
     *            True if we want to apply style to the fragment.
     * @param format
     *            True if we want to format the fragment.
     */
    private static void processElement(final String frag, final StringBuilder output, final ElementType elementType,
            final boolean style, final boolean format) {
        boolean doneElement = false;
        boolean isAttValue = false;
        boolean lastCharWasSpace = false;

        Style currentStyle = null;
        Style newStyle = null;

        char[] chars = frag.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if (elementType == ElementType.PI || elementType == ElementType.DOCTYPE) {
                if (c == '"') {
                    // Toggle attribute value flag.
                    isAttValue = !isAttValue;
                    newStyle = elementType.getStyle();

                } else if (isAttValue) {
                    newStyle = elementType.getStyle();

                } else if (c == '?' || c == '!' || c == '/' || c == ':' || c == '=' || c == '<' || c == '>') {
                    newStyle = Style.SYNTAX;
                } else {
                    newStyle = elementType.getStyle();
                }

            } else if (elementType == ElementType.COMMENT) {
                if (c == '!' || c == '-' || c == '<' || c == '>') {
                    newStyle = Style.SYNTAX;
                } else {
                    newStyle = elementType.getStyle();
                }

            } else {
                if (c == '"') {
                    // Toggle attribute value flag.
                    isAttValue = !isAttValue;
                    newStyle = Style.ATTRIBUTE_VALUE;

                } else if (isAttValue) {
                    newStyle = Style.ATTRIBUTE_VALUE;

                } else if (c == '/' || c == ':' || c == '=' || c == '<' || c == '>') {
                    newStyle = Style.SYNTAX;

                } else {
                    if (!doneElement && (c == ' ' || c == '\n' || c == '\t')) {
                        doneElement = true;
                        newStyle = Style.SYNTAX;
                    }

                    if (newStyle != Style.ATTRIBUTE_NAME && newStyle != Style.ELEMENT_NAME) {
                        if (doneElement) {
                            newStyle = Style.ATTRIBUTE_NAME;
                        } else {
                            newStyle = elementType.getStyle();
                        }
                    }
                }
            }

            // Output the start style element if the style has changed.
            if (style) {
                // Set the style.
                if (currentStyle == null) {
                    output.append(newStyle.getHTML());
                } else if (currentStyle != newStyle) {
                    output.append(END_SPAN);
                    output.append(newStyle.getHTML());
                }
                currentStyle = newStyle;
            }

            // Output the character.
            if (c == '<') {
                output.append(LT);
                lastCharWasSpace = false;
            } else if (c == '>') {
                output.append(GT);
                lastCharWasSpace = false;
            } else if (format && !isAttValue && (c == ' ' || c == '\n' || c == '\t')) {
                if (!lastCharWasSpace) {
                    output.append(' ');
                }
                lastCharWasSpace = true;
            } else {
                output.append(c);
                lastCharWasSpace = false;
            }
        }

        // Close the final span element.
        if (style) {
            output.append(END_SPAN);
        }
    }
}
