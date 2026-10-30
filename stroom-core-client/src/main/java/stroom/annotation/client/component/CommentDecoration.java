/*
 * Copyright 2026 Crown Copyright
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

package stroom.annotation.client.component;

import stroom.annotation.shared.AnnotationEntryType;
import stroom.annotation.shared.EventId;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;

/// The comments and references in an annotation's history ([AnnotationHistory]): `#<digits>` at the
/// start of a word becomes a link to that annotation, and `<streamId>:<eventId>` a link to that
/// event, each a link the keyboard reaches and follows (with Enter). It is apart from the history,
/// a widget, so that it can be tested on the JVM.
final class CommentDecoration {

    private static final Attribute ANNOTATION_LINK = Attribute.className("annotation-history__link");
    // An event or annotation reference is a link the keyboard can reach and follow (with Enter)
    private static final Attribute LINK_ROLE = new Attribute("role", "link");
    private static final Attribute LINK_TAB_INDEX = new Attribute("tabindex", "0");
    // Put between references written with nothing between them: an em space
    private static final String LINK_GAP = "\u2003";

    private CommentDecoration() {
        // Static utility
    }

    /// Adds a comment to the history, its links made clickable.
    ///
    /// @param html    The history being built.
    /// @param comment The comment.
    static void decorateComment(final HtmlBuilder html, final String comment) {
        final StringBuilder sb = new StringBuilder();
        final char[] chars = comment.toCharArray();

        boolean start = true;
        // Whether a link has been added (so one added next with no text before it needs a space)
        boolean afterLink = false;
        for (int i = 0; i < chars.length; i++) {
            final char c = chars[i];
            if (start && c == '#') {
                // We may have an annotation ref.

                // Get the string.
                final String idString = getNumberString(chars, i + 1);
                if (idString != null) {
                    // Append current buffer.
                    if (sb.length() > 0) {
                        html.append(sb.toString());
                        sb.setLength(0);
                    } else if (afterLink) {
                        // Links written with nothing between them (#5#6) are spaced apart (an
                        // em space, wide enough that each is a target of its own, WCAG 2.5.8)
                        html.append(LINK_GAP);
                    }

                    // Append the link.
                    link(html, AnnotationEntryType.LINK_ANNOTATION, idString);
                    afterLink = true;

                    // Jump to next index.
                    i += idString.length();
                } else {
                    sb.append(c);
                }

            } else if (start && Character.isDigit(c)) {
                // We may have an event id.
                boolean isEventId = false;

                int pos = i;
                final String streamIdString = getNumberString(chars, pos);
                if (streamIdString != null) {
                    pos += streamIdString.length();
                    if (chars.length > pos && chars[pos] == ':') {
                        pos++;
                        final String eventIdString = getNumberString(chars, pos);
                        if (eventIdString != null) {
                            pos += eventIdString.length();

                            // Append current buffer.
                            if (sb.length() > 0) {
                                html.append(sb.toString());
                                sb.setLength(0);
                            } else if (afterLink) {
                                html.append(LINK_GAP);
                            }

                            // Append the link.
                            link(html, AnnotationEntryType.LINK_EVENT, streamIdString + ":" + eventIdString);
                            afterLink = true;

                            // Jump to next index.
                            i = pos - 1;
                            isEventId = true;
                        }
                    }
                }

                if (!isEventId) {
                    sb.append(c);
                }

            } else {
                start = Character.isWhitespace(c);
                sb.append(c);
            }
        }
        // Add remaining content.
        html.append(sb.toString());
    }

    /// Adds a reference to an event or an annotation as a link the keyboard reaches and follows
    /// (with Enter), or any other value in bold.
    ///
    /// @param builder   The history being built.
    /// @param entryType The type of the entry the value is from.
    /// @param value     The value, e.g. an event id (`<streamId>:<eventId>`) or an annotation id.
    static void link(final HtmlBuilder builder,
                             final AnnotationEntryType entryType,
                             final String value) {
        if (AnnotationEntryType.LINK_EVENT.equals(entryType) ||
            AnnotationEntryType.UNLINK_EVENT.equals(entryType)) {
            final EventId eventId = EventId.parse(value);
            if (eventId != null) {
                builder.underline(u -> u.append(eventId.toString()),
                        ANNOTATION_LINK,
                        new Attribute("eventId", eventId.toString()),
                        LINK_ROLE,
                        LINK_TAB_INDEX,
                        new Attribute("aria-label", "Event " + eventId));
            } else {
                builder.bold(value);
            }
        } else if (AnnotationEntryType.LINK_ANNOTATION.equals(entryType) ||
                   AnnotationEntryType.UNLINK_ANNOTATION.equals(entryType)) {
            builder.underline(u -> {
                u.append("#");
                u.append(value);
            }, ANNOTATION_LINK,
                    new Attribute("annotationId", value),
                    LINK_ROLE,
                    LINK_TAB_INDEX,
                    new Attribute("aria-label", "Annotation " + value));
        } else {
            builder.bold(value);
        }
    }

    private static String getNumberString(final char[] chars, final int start) {
        // Try to get a number.
        int len = 0;
        for (int i = start; i < chars.length; i++) {
            final char c = chars[i];
            if (Character.isDigit(c)) {
                len++;
            } else {
                break;
            }
        }
        if (len > 0) {
            try {
                // Get the string.
                final String idString = new String(chars, start, len);
                // Make sure it is a valid number.
                Long.parseLong(idString);
                return idString;
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }
        return null;
    }
}
