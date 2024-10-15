/*
 * Copyright 2017 Crown Copyright
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

package stroom.query.client.presenter;

import stroom.dashboard.client.table.ColumnsManager;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Filter;
import stroom.query.api.v2.Sort;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.CompositeCell;
import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.HasCell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextInputCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;

public class ColumnHeaderCell extends CompositeCell<Column> {

    public ColumnHeaderCell(final List<HasCell<Column, ?>> cells) {
        super(cells);
    }

    public static ColumnHeaderCell create(final ColumnsManager columnsManager) {
        final com.google.gwt.user.cellview.client.Column<Column, SafeHtml> name =
                new com.google.gwt.user.cellview.client.Column<Column, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final Column column) {
                        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
                        sb.appendHtmlConstant("<div class=\"column-top\">");

                        // Output name.
                        sb.appendHtmlConstant("<div class=\"column-label\">");
                        sb.appendEscaped(column.getName());
                        sb.appendHtmlConstant("</div>");

                        // Show group icon.
                        if (column.getGroup() != null) {
                            // Show group icon.
                            sb.append(getSafeHtml(SvgImage.FIELDS_GROUP));

                            // Show group depth.
                            sb.append(SafeHtmlUtils
                                    .fromTrustedString("<div class=\"column-sortOrder\">" +
                                            (column.getGroup() + 1) +
                                            "</div>"));
                        }

                        // Add sort icon.
                        if (column.getSort() != null) {
                            if (Sort.SortDirection.ASCENDING == column.getSort().getDirection()) {
                                sb.append(getSafeHtml(SvgImage.FIELDS_SORTAZ));
                            } else {
                                sb.append(getSafeHtml(SvgImage.FIELDS_SORTZA));
                            }

                            // Add sort order.
                            sb.append(SafeHtmlUtils
                                    .fromTrustedString("<div class=\"column-sortOrder\">" +
                                            (column.getSort().getOrder() + 1) +
                                            "</div>"));
                        }

                        // Add filter icon.
                        final Filter filter = column.getFilter();
                        if (filter != null) {
                            if ((filter.getIncludes() != null && filter.getIncludes().trim().length() > 0) ||
                                    (filter.getExcludes() != null && filter.getExcludes().trim().length() > 0)) {
                                sb.append(getSafeHtml(SvgImage.FIELDS_FILTER));
                            }
                        }

                        sb.appendHtmlConstant("</div>");

                        return sb.toSafeHtml();
                    }
                };

        final com.google.gwt.user.cellview.client.Column<Column, String> filterInput =
                new com.google.gwt.user.cellview.client.Column<Column, String>(
                        new TextInputCell()) {
                    @Override
                    public String getValue(final Column column) {
                        return column.getValueFilter();
                    }
                };
        filterInput.setFieldUpdater(new FieldUpdater<Column, String>() {
            @Override
            public void update(final int index, final Column column, final String value) {
                columnsManager.setValueFilter(column, value);
            }
        });

        final List<HasCell<Column, ?>> list = new ArrayList<>();
        list.add(name);
        list.add(filterInput);

        return new ColumnHeaderCell(list);
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }
}
