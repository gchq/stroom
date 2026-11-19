/*
 * Copyright 2023 Crown Copyright
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

package stroom.widget.util.client;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class HtmlTableBuilder {

    private final List<HtmlTableRow> rows;

    public HtmlTableBuilder() {
        rows = new ArrayList<>();
    }

    public HtmlTableBuilder row(final String... values) {
        final List<HtmlTableCell> cells = new ArrayList<>();
        for (final String value : values) {
            cells.add(HtmlTableCell.builder().value(value).build());
        }
        final HtmlTableRow row = new HtmlTableRow(cells);
        rows.add(row);
        return this;
    }

    public HtmlTableBuilder row(final SafeHtml... values) {
        final List<HtmlTableCell> cells = new ArrayList<>();
        for (final SafeHtml value : values) {
            cells.add(HtmlTableCell.builder().value(value).build());
        }
        final HtmlTableRow row = new HtmlTableRow(cells);
        rows.add(row);
        return this;
    }

    public HtmlTableBuilder row(final HtmlTableCell... cells) {
        return row(new HtmlTableRow(Arrays.asList(cells)));
    }

    public HtmlTableBuilder row() {
        return row(new HtmlTableRow(Collections.emptyList()));
    }

    public HtmlTableBuilder row(final HtmlTableRow row) {
        rows.add(row);
        return this;
    }

    public void write(final HtmlBuilder htmlBuilder) {
        htmlBuilder.appendTrustedString("<table>");

        for (final HtmlTableRow row : rows) {
            htmlBuilder.appendTrustedString("<tr>");
            for (final HtmlTableCell cell : row.getCells()) {
                cell.write(htmlBuilder);
            }
            htmlBuilder.appendTrustedString("</tr>");
        }

        htmlBuilder.appendTrustedString("</table>");
    }
}
