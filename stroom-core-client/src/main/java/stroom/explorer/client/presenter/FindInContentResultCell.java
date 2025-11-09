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

import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.shared.DocContentMatch;
import stroom.explorer.shared.FindInContentResult;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;

public class FindInContentResultCell extends AbstractCell<FindInContentResult> {

    @Override
    public void render(final Context context, final FindInContentResult value, final SafeHtmlBuilder sb) {
        if (value != null) {
            final DocContentMatch match = value.getDocContentMatch();
            final SafeHtmlBuilder row = new SafeHtmlBuilder();
            final SafeHtmlBuilder main = new SafeHtmlBuilder();
            final SafeHtmlBuilder sub = new SafeHtmlBuilder();
            final SafeHtmlBuilder tags = new SafeHtmlBuilder();

            // Add icon
            final DocumentType documentType = DocumentTypeRegistry.get(
                    value.getDocContentMatch().getDocRef().getType());
            if (documentType != null && documentType.getIcon() != null) {
                main.append(SvgImageUtil.toSafeHtml(
                        match.getDocRef().getType(),
                        documentType.getIcon(),
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
            final SafeHtmlBuilder sampleHtml = new SafeHtmlBuilder();
            sampleHtml.append(Templates.div(getCellClassName() + "-sample-before",
                    SafeHtmlUtil.from(sampleBefore)));
            sampleHtml.append(Templates.div(getCellClassName() + "-highlight",
                    SafeHtmlUtil.from(highlight)));
            sampleHtml.append(Templates.div(getCellClassName() + "-sample-after",
                    SafeHtmlUtil.from(sampleAfter)));

            String sampleClass = getCellClassName() + "-sample";
            // Different styling for samples that do not start at beginning of line
            if (!match.isSampleAtStartOfLine()) {
                sampleClass = sampleClass + " " + getCellClassName() + "-sample-truncated";
            }
            main.append(Templates.div(
                    sampleClass,
                    value.getDocContentMatch().getSample(),
                    sampleHtml.toSafeHtml()));

            // Add name
            final String name = match.getDocRef().getName();
            main.append(Templates.div(
                    getCellClassName() + "-name",
                    value.getPath() + " / " + name,
                    SafeHtmlUtil.from(name)));

            row.append(Templates.div(getCellClassName() + "-main", main.toSafeHtml()));

            // Add path
            sub.append(Templates.div(getCellClassName() + "-path",
                    SafeHtmlUtil.from(value.getPath())));

            // Add uuid
            sub.append(Templates.div(getCellClassName() + "-uuid",
                    SafeHtmlUtil.from(match.getDocRef().getUuid())));

            row.append(Templates.div(getCellClassName() + "-sub", sub.toSafeHtml()));

            if (!value.getDocContentMatch().getTags().isEmpty()) {
                final String tagsString = "Tags: " + value.getDocContentMatch().getTags().toString();
                tags.append(Templates.div(getCellClassName() + "-tags", SafeHtmlUtil.from(tagsString)));
                row.append(Templates.div(getCellClassName() + "-sub", tags.toSafeHtml()));
            }

            sb.append(Templates.div(getCellClassName() + "-row", row.toSafeHtml()));
        }
    }

    private String getCellClassName() {
        return "findCell";
    }
}
