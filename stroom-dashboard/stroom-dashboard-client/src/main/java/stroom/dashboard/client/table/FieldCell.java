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

package stroom.dashboard.client.table;

import com.google.gwt.cell.client.CompositeCell;
import com.google.gwt.cell.client.HasCell;
import com.google.gwt.cell.client.ImageResourceCell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import stroom.dashboard.shared.Field;
import stroom.dashboard.shared.Filter;
import stroom.dashboard.shared.Sort;

import java.util.ArrayList;
import java.util.List;

public class FieldCell extends CompositeCell<Field> {
    private final FieldsManager fieldsManager;

    public FieldCell(final FieldsManager fieldsManager, final List<HasCell<Field, ?>> cells) {
        super(cells);
        this.fieldsManager = fieldsManager;
    }

    public static FieldCell create(final FieldsManager fieldsManager) {
        final List<HasCell<Field, ?>> cells = new ArrayList<>();

        final Column<Field, SafeHtml> name = new Column<Field, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Field field) {
                final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                builder.appendHtmlConstant("<div class=\"" + fieldsManager.getResources().style().fieldLabel() + "\">");
                builder.appendEscaped(field.getName());
                builder.appendHtmlConstant("</div>");
                return builder.toSafeHtml();
            }
        };
        cells.add(name);

        final Column<Field, ImageResource> group = new Column<Field, ImageResource>(new ImageResourceCell()) {
            @Override
            public ImageResource getValue(final Field field) {
                if (field.getGroup() == null) {
                    return null;
                } else {
                    return fieldsManager.getResources().group();
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
                            .fromTrustedString("<div class=\"" + fieldsManager.getResources().style().sortOrder()
                                    + "\">" + (field.getGroup() + 1) + "</div>");
                }
            }
        };
        cells.add(groupNo);

        final Column<Field, ImageResource> sort = new Column<Field, ImageResource>(new ImageResourceCell()) {
            @Override
            public ImageResource getValue(final Field field) {
                if (field.getSort() == null) {
                    return null;
                } else if (Sort.SortDirection.ASCENDING == field.getSort().getDirection()) {
                    return fieldsManager.getResources().sortaz();
                } else {
                    return fieldsManager.getResources().sortza();
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
                            .fromTrustedString("<div class=\"" + fieldsManager.getResources().style().sortOrder()
                                    + "\">" + (field.getSort().getOrder() + 1) + "</div>");
                }
            }
        };
        cells.add(sortOrder);


        final Column<Field, ImageResource> filter = new Column<Field, ImageResource>(new ImageResourceCell()) {
            @Override
            public ImageResource getValue(final Field field) {
                final Filter filter = field.getFilter();
                if (filter != null) {
                    if ((filter.getIncludes() != null && filter.getIncludes().trim().length() > 0) ||
                            (filter.getExcludes() != null && filter.getExcludes().trim().length() > 0)) {
                        return fieldsManager.getResources().filter();
                    }
                }

                return null;
            }
        };
        cells.add(filter);

        return new FieldCell(fieldsManager, cells);
    }

    @Override
    public void render(Context context, Field value, SafeHtmlBuilder sb) {
        sb.appendHtmlConstant("<div class=\"" + fieldsManager.getResources().style().field() + "\">");
        super.render(context, value, sb);
        sb.appendHtmlConstant("</div>");
    }
}
