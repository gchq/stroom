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

package stroom.item.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class SelectionItemCell<I extends SelectionItem> extends AbstractCell<I> {

    private static Template template;

    public SelectionItemCell() {
        if (template == null) {
            template = GWT.create(Template.class);
        }
    }

    private String getCellClassName() {
        return "selectionItemCell";
    }

    @Override
    public void render(final Context context, final I row, final SafeHtmlBuilder sb) {
        if (row != null) {
            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            if (row.getIcon() != null) {
                // Add icon
                final String title = NullSafe.requireNonNullElseGet(
                        row.getIconTooltip(),
                        row::getLabel);
                final SafeHtml iconSafeHtml = SvgImageUtil.toSafeHtml(
                        title,
                        row.getIcon(),
                        "explorerCell-icon");
                content.append(iconSafeHtml);
            }
            if (row.hasRenderedLabel()) {
                content.append(template.div(
                        getCellClassName() + "-content",
                        row.getRenderedLabel()));
            } else {
                content.append(template.div(
                        getCellClassName() + "-text",
                        SafeHtmlUtil.getSafeHtml(row.getLabel())));
            }

            // Add parent indicator arrow.
            if (row.isHasChildren()) {
                final SvgImage expanderIcon = SvgImage.ARROW_RIGHT;
                final SafeHtml expanderIconSafeHtml;
                String className = getCellClassName() + "-expander";
                className += " " + expanderIcon.getClassName();
                expanderIconSafeHtml = SafeHtmlUtils.fromTrustedString(expanderIcon.getSvg());
                content.append(template.div(className, expanderIconSafeHtml));
            }

            sb.append(template.div("explorerCell", content.toSafeHtml()));
        }
    }

    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"{0}\">{1}</div>")
        SafeHtml div(String className, SafeHtml content);
    }
}
