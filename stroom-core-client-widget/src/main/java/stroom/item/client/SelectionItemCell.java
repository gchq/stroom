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

package stroom.item.client;

import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class SelectionItemCell<I extends SelectionItem> extends AbstractCell<I> {

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
                content.append(Templates.div(
                        getCellClassName() + "-content",
                        row.getRenderedLabel()));
            } else {
                content.append(Templates.div(
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
                content.append(Templates.div(className, expanderIconSafeHtml));
            }

            sb.append(Templates.div("explorerCell", content.toSafeHtml()));
        }
    }
}
