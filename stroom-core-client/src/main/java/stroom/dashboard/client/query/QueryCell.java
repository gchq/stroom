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

package stroom.dashboard.client.query;

import stroom.dashboard.shared.StoredQuery;
import stroom.preferences.client.DateTimeFormatter;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class QueryCell extends AbstractCell<StoredQuery> {

    private static Template template;

    private final DateTimeFormatter dateTimeFormatter;

    public QueryCell(final DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
        if (template == null) {
            synchronized (QueryCell.class) {
                if (template == null) {
                    template = GWT.create(Template.class);
                }
            }
        }
    }

    @Override
    public void render(final Context context, final StoredQuery value, final SafeHtmlBuilder sb) {
        if (value != null) {
            if (value.isFavourite()) {
                sb.append(template.favouritesLayout("queryCell-outer", "queryCell-expression",
                        value.getName()));

            } else {
                final String time = dateTimeFormatter.format(value.getCreateTimeMs());
                sb.append(template.historyLayout("queryCell-outer", "queryCell-time", time,
                        "queryCell-expression", value.getQuery().getExpression().toString()));
            }
        }
    }

    public interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\"><span class=\"{1}\">{2}</span><span class=\"{3}\">{4}</span></div>")
        SafeHtml historyLayout(String outerClassName, String timeClassName, String time, String expressionClassName,
                               String expression);

        @Template("<div class=\"{0}\"><span class=\"{1}\">{2}</span></div>")
        SafeHtml favouritesLayout(String outerClassName, String nameClassName, String name);
    }
}
