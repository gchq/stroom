/*
 * Copyright 2024 Crown Copyright
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

package stroom.explorer.client.presenter;

import stroom.docref.DocContentMatch;
import stroom.explorer.shared.FindInContentResult;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class FindInContentResultCell extends AbstractCell<FindInContentResult> {

    private static Template template;

    public FindInContentResultCell() {
        if (template == null) {
            synchronized (FindInContentResult.class) {
                if (template == null) {
                    template = GWT.create(Template.class);
                }
            }
        }
    }

    @Override
    public void render(final Context context, final FindInContentResult value, final SafeHtmlBuilder sb) {
        if (value != null) {
            final DocContentMatch match = value.getDocContentMatch();
            final SafeHtmlBuilder row = new SafeHtmlBuilder();
            final SafeHtmlBuilder main = new SafeHtmlBuilder();
            final SafeHtmlBuilder sub = new SafeHtmlBuilder();

            // Add icon
            if (value.getIcon() != null) {
                main.append(SvgImageUtil.toSafeHtml(
                        match.getDocRef().getType(),
                        value.getIcon(),
                        getCellClassName() + "-icon",
                        "svgIcon"));
            }

            // Add sample
            final String sample = match.getSample();
            final int start = Math.max(
                    match.getLocation().getOffset(),
                    0);
            final int end = Math.min(
                    match.getLocation().getOffset() + match.getLocation().getLength(),
                    sample.length());
            final String sampleBefore = sample.substring(0, start)
                    .replaceAll("\n", " ");
            final String highlight = sample.substring(start, end)
                    .replaceAll("\n", " ");
            final String sampleAfter = sample.substring(Math.min(end, sample.length()))
                    .replaceAll("\n", " ");
            SafeHtmlBuilder sampleHtml = new SafeHtmlBuilder();
            sampleHtml.append(template.div(getCellClassName() + "-sample-before",
                    SafeHtmlUtil.from(sampleBefore)));
            sampleHtml.append(template.div(getCellClassName() + "-highlight",
                    SafeHtmlUtil.from(highlight)));
            sampleHtml.append(template.div(getCellClassName() + "-sample-after",
                    SafeHtmlUtil.from(sampleAfter)));

            main.append(template.divWithTitle(
                    getCellClassName() + "-sample",
                    value.getDocContentMatch().getSample(),
                    sampleHtml.toSafeHtml()));

            // Add name
            final String name = match.getDocRef().getName();
            main.append(template.divWithTitle(
                    getCellClassName() + "-name",
                    value.getPath() + " / " + name,
                    SafeHtmlUtil.from(name)));

            row.append(template.div(getCellClassName() + "-main", main.toSafeHtml()));

            // Add path
            sub.append(template.div(getCellClassName() + "-path",
                    SafeHtmlUtil.from(value.getPath())));

            // Add uuid
            sub.append(template.div(getCellClassName() + "-uuid",
                    SafeHtmlUtil.from(match.getDocRef().getUuid())));

            row.append(template.div(getCellClassName() + "-sub", sub.toSafeHtml()));

            sb.append(template.div(getCellClassName() + "-row", row.toSafeHtml()));
        }
    }

    private String getCellClassName() {
        return "findCell";
    }


    // --------------------------------------------------------------------------------


    public interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);

        @Template("<div class=\"{0}\" title=\"{1}\">{2}</div>")
        SafeHtml divWithTitle(String className, String title, SafeHtml content);
    }
}
