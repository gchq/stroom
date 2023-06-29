/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.explorer.client.presenter;

import stroom.explorer.shared.ExplorerDocContentMatch;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class ExplorerDocContentMatchCell extends AbstractCell<ExplorerDocContentMatch> {

    private static Template template;

    public ExplorerDocContentMatchCell() {
        if (template == null) {
            synchronized (ExplorerDocContentMatch.class) {
                if (template == null) {
                    template = GWT.create(Template.class);
                }
            }
        }
    }

    @Override
    public void render(final Context context, final ExplorerDocContentMatch value, final SafeHtmlBuilder sb) {
        if (value != null) {
            final SafeHtmlBuilder row = new SafeHtmlBuilder();
            final SafeHtmlBuilder main = new SafeHtmlBuilder();
            final SafeHtmlBuilder sub = new SafeHtmlBuilder();

            // Add icon
            main.append(template.icon(getCellClassName() + "-icon",
                    value.getDocContentMatch().getDocRef().getType(),
                    SafeHtmlUtils.fromSafeConstant(value.getIcon().getSvg())));

            // Add sample
            main.append(template.div(getCellClassName() + "-sample",
                    SafeHtmlUtil.from(value.getDocContentMatch().getSample())));

            // Add name
            main.append(template.div(getCellClassName() + "-name",
                    SafeHtmlUtil.from(value.getDocContentMatch().getDocRef().getName())));

            row.append(template.div(getCellClassName() + "-main", main.toSafeHtml()));

            // Add path
            sub.append(template.div(getCellClassName() + "-path",
                    SafeHtmlUtil.from(value.getPath())));

            // Add uuid
            sub.append(template.div(getCellClassName() + "-uuid",
                    SafeHtmlUtil.from(value.getDocContentMatch().getDocRef().getUuid())));

            row.append(template.div(getCellClassName() + "-sub", sub.toSafeHtml()));

            sb.append(template.div(getCellClassName() + "-row", row.toSafeHtml()));
        }
    }

    private String getCellClassName() {
        return "findCell";
    }

    public interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml icon(String iconClass, String typeName, SafeHtml icon);

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);
    }
}
