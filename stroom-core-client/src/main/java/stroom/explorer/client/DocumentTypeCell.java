/*
 * Copyright 2021 Crown Copyright
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

package stroom.explorer.client;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.docstore.shared.DocumentType;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class DocumentTypeCell extends AbstractCell<DocumentType> {

    private static final TickBoxCell.DefaultAppearance TICK_BOX = new TickBoxCell.DefaultAppearance();

    private final DocumentTypeSelectionModel selectionModel;

    public DocumentTypeCell(final DocumentTypeSelectionModel selectionModel) {
        this.selectionModel = selectionModel;
    }

    @Override
    public void render(final Context context, final DocumentType item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final SafeHtml iconHtml = SvgImageUtil.toSafeHtml(item.getIcon(), "explorer-cell__icon");

            final SafeHtml textHtml = Templates.div("explorer-cell__text",
                    SafeHtmlUtils.fromString(item.getDisplayType()));

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            // The tick box is a picture: the list's option says whether it is ticked
            content.append(TICK_BOX.getPicture(selectionModel.getState(item)));
            content.append(iconHtml);
            content.append(textHtml);

            sb.append(Templates.div("explorer-cell", content.toSafeHtml()));
        }
    }
}
