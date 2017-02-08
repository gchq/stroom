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

package stroom.dashboard.client.query;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import stroom.dashboard.shared.Query;
import stroom.widget.customdatebox.client.ClientDateUtil;

public class QueryCell extends AbstractCell<Query> {
    private static Template template;
    private static Resources resources;

    public QueryCell() {
        if (template == null) {
            synchronized (QueryCell.class) {
                if (template == null) {
                    template = GWT.create(Template.class);
                    resources = GWT.create(Resources.class);
                    resources.style().ensureInjected();
                }
            }
        }
    }

    @Override
    public void render(final Context context, final Query value, final SafeHtmlBuilder sb) {
        if (value != null) {
            if (value.isFavourite()) {
                sb.append(template.favouritesLayout(resources.style().outer(), resources.style().expression(),
                        value.getName()));

            } else {
                final String time = ClientDateUtil.createDateTimeString(value.getCreateTime());
                final StringBuilder expression = new StringBuilder();
                value.getQueryData().getExpression().append(expression, "", true);

                sb.append(template.historyLayout(resources.style().outer(), resources.style().time(), time,
                        resources.style().expression(), expression.toString()));
            }
        }
    }

    public interface Style extends CssResource {
        String outer();

        String time();

        String expression();
    }

    public interface Resources extends ClientBundle {
        @Source("querycell.css")
        Style style();
    }

    public interface Template extends SafeHtmlTemplates {
        @Template("<div class=\"{0}\"><span class=\"{1}\">{2}</span><span class=\"{3}\">{4}</span></div>")
        SafeHtml historyLayout(String outerClassName, String timeClassName, String time, String expressionClassName,
                               String expression);

        @Template("<div class=\"{0}\"><span class=\"{1}\">{2}</span></div>")
        SafeHtml favouritesLayout(String outerClassName, String nameClassName, String name);
    }
}
