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

import stroom.query.api.v2.Column;
import stroom.query.api.v2.Filter;
import stroom.query.api.v2.Sort;
import stroom.svg.shared.SvgImage;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.CompositeCell;
import com.google.gwt.cell.client.HasCell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import java.util.ArrayList;
import java.util.List;

public class TableCell extends CompositeCell<Column> {

    public TableCell(final List<HasCell<Column, ?>> cells) {
        super(cells);
    }

    public static TableCell create() {
        final List<HasCell<Column, ?>> cells = new ArrayList<>();

        final com.google.gwt.user.cellview.client.Column<Column, SafeHtml> name =
                new com.google.gwt.user.cellview.client.Column<Column, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final Column column) {
                        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                        builder.appendHtmlConstant("<div class=\"fields-fieldLabel\">");
                        builder.appendEscaped(column.getName());
                        builder.appendHtmlConstant("</div>");
                        return builder.toSafeHtml();
                    }
                };
        cells.add(name);

        final com.google.gwt.user.cellview.client.Column<Column, SafeHtml> group =
                new com.google.gwt.user.cellview.client.Column<Column, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final Column column) {
                        if (column.getGroup() == null) {
                            return null;
                        } else {
                            return getSafeHtml(SvgImage.FIELDS_GROUP);
                        }
                    }
                };
        cells.add(group);

        final com.google.gwt.user.cellview.client.Column<Column, SafeHtml> groupNo =
                new com.google.gwt.user.cellview.client.Column<Column, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final Column column) {
                        if (column.getGroup() == null) {
                            return SafeHtmlUtils.EMPTY_SAFE_HTML;
                        } else {
                            return SafeHtmlUtils
                                    .fromTrustedString("<div class=\"fields-sortOrder\">" +
                                            (column.getGroup() + 1) +
                                            "</div>");
                        }
                    }
                };
        cells.add(groupNo);

        final com.google.gwt.user.cellview.client.Column<Column, SafeHtml> sort =
                new com.google.gwt.user.cellview.client.Column<Column, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final Column column) {
                        if (column.getSort() == null) {
                            return null;
                        } else if (Sort.SortDirection.ASCENDING == column.getSort().getDirection()) {
                            return getSafeHtml(SvgImage.FIELDS_SORTAZ);
                        } else {
                            return getSafeHtml(SvgImage.FIELDS_SORTZA);
                        }
                    }
                };
        cells.add(sort);

        final com.google.gwt.user.cellview.client.Column<Column, SafeHtml> sortOrder =
                new com.google.gwt.user.cellview.client.Column<Column, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final Column column) {
                        if (column.getSort() == null) {
                            return SafeHtmlUtils.EMPTY_SAFE_HTML;
                        } else {
                            return SafeHtmlUtils
                                    .fromTrustedString("<div class=\"fields-sortOrder\">" +
                                            (column.getSort().getOrder() + 1) +
                                            "</div>");
                        }
                    }
                };
        cells.add(sortOrder);


        final com.google.gwt.user.cellview.client.Column<Column, SafeHtml> filter =
                new com.google.gwt.user.cellview.client.Column<Column, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final Column column) {
                        final Filter filter = column.getFilter();
                        if (filter != null) {
                            if ((filter.getIncludes() != null && filter.getIncludes().trim().length() > 0) ||
                                    (filter.getExcludes() != null && filter.getExcludes().trim().length() > 0)) {
                                return getSafeHtml(SvgImage.FIELDS_FILTER);
                            }
                        }

                        return null;
                    }
                };
        cells.add(filter);

        return new TableCell(cells);
    }

    @Override
    public void render(Context context, Column column, SafeHtmlBuilder sb) {
        sb.appendHtmlConstant("<div class=\"fields-field\">");
        super.render(context, column, sb);
        sb.appendHtmlConstant("</div>");
    }

    private static SafeHtml getSafeHtml(final SvgImage svgImage) {
        return SvgImageUtil.toSafeHtml(svgImage, "svgIcon");
    }
}
