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
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Row;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.widget.util.client.MultiSelectionModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class TableCell extends AbstractCell<Row> {
    private static final Set<String> ENABLED_EVENTS = Collections.singleton("click");

    private final TablePresenter tablePresenter;
    private final MultiSelectionModel<Row> selectionModel;
    private final Field field;

    TableCell(final TablePresenter tablePresenter, final MultiSelectionModel<Row> selectionModel, final Field field, final int pos) {
        super(ENABLED_EVENTS);
        this.tablePresenter = tablePresenter;
        this.selectionModel = selectionModel;
        this.field = field;
    }

    @Override
    public void onBrowserEvent(final Context context, final Element parent, final Row row,
                               final NativeEvent event, final ValueUpdater<Row> valueUpdater) {
        super.onBrowserEvent(context, parent, row, event, valueUpdater);
        final String value = getValue(row);

        boolean handled = false;
        if (value != null && "click".equals(event.getType())) {
            final EventTarget eventTarget = event.getEventTarget();
            if (!Element.is(eventTarget)) {
                return;
            }
            final Element element = eventTarget.cast();
            if (element.hasTagName("u")) {
                final String link = element.getAttribute("link");
                if (link != null) {
                    final Hyperlink hyperlink = Hyperlink.create(link);
                    if (hyperlink != null) {
                        handled = true;
                        HyperlinkEvent.fire(tablePresenter, hyperlink);
                    }
                }
            }
        }

        if (!handled) {
            selectionModel.setSelected(row);
        }
    }

    @Override
    public void render(final Context context, final Row row, final SafeHtmlBuilder sb) {
        final String value = getValue(row);
        if (value != null) {
            final boolean grouped = field.getGroup() != null && field.getGroup() >= row.getDepth();
            if (grouped) {
                sb.appendHtmlConstant("<b>");
            }
            append(value, sb, field);
            if (grouped) {
                sb.appendHtmlConstant("</b>");
            }
        }
    }

    private String getValue(final Row row) {
        if (row != null) {
            final List<String> values = row.getValues();
            if (values != null) {
                final List<Field> fields = tablePresenter.getCurrentFields();
                final int index = fields.indexOf(field);
                if (index != -1 && values.size() > index) {
                    return values.get(index);
                }
            }
        }
        return null;
    }

    static void append(final String value, final SafeHtmlBuilder sb, final Field field) {
        final List<Object> parts = getParts(value);
        parts.forEach(p -> {
            if (p instanceof Hyperlink) {
                final Hyperlink hyperlink = (Hyperlink) p;
                if (!hyperlink.getText().trim().isEmpty()) {
                    sb.appendHtmlConstant("<u link=\"" + hyperlink.toString() + "\">");

//                    if (field != null && field.getFormat() != null && field.getFormat().getType() == Type.DATE_TIME) {
//                        try {
//                            long l = Long.parseLong(hyperlink.getText());
//                            sb.appendEscaped(ClientDateUtil.toISOString(l));
//                        } catch (final RuntimeException e) {
//                            sb.appendEscaped(hyperlink.getText());
//                        }
//                    } else {
                    sb.appendEscaped(hyperlink.getText());
//                    }

                    sb.appendHtmlConstant("</u>");
                }
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
                final Hyperlink hyperlink = Hyperlink.create(value, i);
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
}
