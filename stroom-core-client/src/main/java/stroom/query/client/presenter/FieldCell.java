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

import stroom.query.api.v2.Field;
import stroom.query.api.v2.Filter;
import stroom.query.api.v2.Sort;

import com.google.gwt.cell.client.CompositeCell;
import com.google.gwt.cell.client.HasCell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;

import java.util.ArrayList;
import java.util.List;

public class FieldCell extends CompositeCell<Field> {

    public FieldCell(final List<HasCell<Field, ?>> cells) {
        super(cells);
    }

    public static FieldCell create() {
        final List<HasCell<Field, ?>> cells = new ArrayList<>();

        final Column<Field, SafeHtml> name = new Column<Field, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Field field) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<div class=\"fields-fieldLabel\">");
                builder.appendEscaped(field.getName());
                builder.appendHtmlConstant("</div>");
                return builder.toSafeHtml();
            }
        };
        cells.add(name);

        final Column<Field, SafeHtml> group = new Column<Field, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Field field) {
                if (field.getGroup() == null) {
                    return null;
                } else {
                    return SafeHtmlUtils.fromTrustedString("<div class=\"svgIcon fields-group\"></div>");
                }
            }
        };
        cells.add(group);

        final Column<Field, SafeHtml> groupNo = new Column<Field, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Field field) {
                if (field.getGroup() == null) {
                    return SafeHtmlUtils.EMPTY_SAFE_HTML;
                } else {
                    return SafeHtmlUtils
                            .fromTrustedString("<div class=\"fields-sortOrder\">" +
                                    (field.getGroup() + 1) +
                                    "</div>");
                }
            }
        };
        cells.add(groupNo);

        final Column<Field, SafeHtml> sort = new Column<Field, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Field field) {
                if (field.getSort() == null) {
                    return null;
                } else if (Sort.SortDirection.ASCENDING == field.getSort().getDirection()) {
                    return SafeHtmlUtils.fromTrustedString("<div class=\"svgIcon fields-sortaz\"></div>");
                } else {
                    return SafeHtmlUtils.fromTrustedString("<div class=\"svgIcon fields-sortza\"></div>");
                }
            }
        };
        cells.add(sort);

        final Column<Field, SafeHtml> sortOrder = new Column<Field, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Field field) {
                if (field.getSort() == null) {
                    return SafeHtmlUtils.EMPTY_SAFE_HTML;
                } else {
                    return SafeHtmlUtils
                            .fromTrustedString("<div class=\"fields-sortOrder\">" +
                                    (field.getSort().getOrder() + 1) +
                                    "</div>");
                }
            }
        };
        cells.add(sortOrder);


        final Column<Field, SafeHtml> filter = new Column<Field, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Field field) {
                final Filter filter = field.getFilter();
                if (filter != null) {
                    if ((filter.getIncludes() != null && filter.getIncludes().trim().length() > 0) ||
                            (filter.getExcludes() != null && filter.getExcludes().trim().length() > 0)) {
                        return SafeHtmlUtils.fromTrustedString("<div class=\"svgIcon fields-filter\"></div>");
                    }
                }

                return null;
            }
        };
        cells.add(filter);

        return new FieldCell(cells);
    }

    @Override
    public void render(Context context, Field value, SafeHtmlBuilder sb) {
        sb.appendHtmlConstant("<div class=\"fields-field\">");
        super.render(context, value, sb);
        sb.appendHtmlConstant("</div>");
    }
}
