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

package stroom.dashboard.client.table;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.cell.client.ValueUpdater;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.EventTarget;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import stroom.dashboard.shared.Field;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.Hyperlink.Builder;
import stroom.hyperlink.client.HyperlinkEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TableCell extends AbstractCell<Row> {
    private static final Set<String> ENABLED_EVENTS = Collections.singleton("click");

    private final HasHandlers hasHandlers;
    private final Field field;
    private final int pos;

    public TableCell(final HasHandlers hasHandlers, final Field field, final int pos) {
        super(ENABLED_EVENTS);
        this.hasHandlers = hasHandlers;
        this.field = field;
        this.pos = pos;
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final Row row,
                               final NativeEvent event, final ValueUpdater<Row> valueUpdater) {
        super.onBrowserEvent(context, parent, row, event, valueUpdater);
        final String value = getValue(row);
        if (value != null && "click".equals(event.getType())) {
            final EventTarget eventTarget = event.getEventTarget();
            if (!Element.is(eventTarget)) {
                return;
            }
            final Element element = eventTarget.cast();
            if (element.hasTagName("u")) {
                final String link = element.getAttribute("link");
                if (link != null) {
                    final Hyperlink hyperlink = getHyperlink(link, 0);
                    if (hyperlink != null) {
                        HyperlinkEvent.fire(hasHandlers, hyperlink);
                    }
                }
            }
        }
    }

    @Override
    public void render(final Context context, final Row row, final SafeHtmlBuilder sb) {
        final String value = getValue(row);
        if (value != null) {
            final boolean grouped = field.getGroup() != null && field.getGroup() >= row.depth;
            if (grouped) {
                sb.appendHtmlConstant("<b>");
            }
            append(value, sb);
            if (grouped) {
                sb.appendHtmlConstant("</b>");
            }
        }
    }

    private String getValue(final Row row) {
        if (row != null) {
            final String[] values = row.values;
            if (values != null) {
                return values[pos];
            }
        }
        return null;
    }

    static void append(final String value, final SafeHtmlBuilder sb) {
        final List<Object> parts = getParts(value);
        parts.forEach(p -> {
            if (p instanceof Hyperlink) {
                final Hyperlink hyperlink = (Hyperlink) p;
                sb.appendHtmlConstant("<u link=\"" + hyperlink.toString() + "\">");
                sb.appendEscaped(hyperlink.getTitle());
                sb.appendHtmlConstant("</u>");
            } else {
                sb.appendEscaped(p.toString());
            }
        });
    }

    private static List<Object> getParts(final String value) {
        final List<Object> parts = new ArrayList<>();

        final StringBuilder sb = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);

            if (c == '[') {
                final Hyperlink hyperlink = getHyperlink(value, i);
                if (hyperlink != null) {
                    if (sb.length() > 0) {
                        parts.add(sb.toString());
                        sb.setLength(0);
                    }
                    parts.add(hyperlink);
                    i += hyperlink.toString().length() - 1;
                } else {
                    sb.append(c);
                }

            } else {
                sb.append(c);
            }
        }

        if (sb.length() > 0) {
            parts.add(sb.toString());
        }

        return parts;
    }

    private static Hyperlink getHyperlink(final String value, final int pos) {
        Hyperlink hyperlink = null;

        int index = pos;
        final String title = nextToken(value, index, '[', ']');
        if (title != null) {
            index = index + title.length() + 2;
            final String href = nextToken(value, index, '(', ')');
            if (href != null) {
                index = index + href.length() + 2;
                final String type = nextToken(value, index, '{', '}');
                hyperlink = new Builder().title(title).href(href).type(type).build();
            }
        }

        return hyperlink;
    }

    private static String nextToken(final String value, final int pos, final char startChar, final char endChar) {
        if (value.length() <= pos + 2 || value.charAt(pos) != startChar) {
            return null;
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = pos + 1; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == endChar) {
                return sb.toString();
            } else if (c == '[' || c == ']' || c == '(' || c == ')' || c == '{' || c == '}') {
                // Unexpected token
                return null;
            } else {
                sb.append(c);
            }
        }
        return null;
    }
}
