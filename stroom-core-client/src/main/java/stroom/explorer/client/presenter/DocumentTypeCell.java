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

package stroom.explorer.client.presenter;

import stroom.cell.tickbox.client.TickBoxCell;
import stroom.docstore.shared.DocumentType;
import stroom.widget.util.client.SvgImageUtil;
import stroom.widget.util.client.Templates;

import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

public class DocumentTypeCell extends AbstractCell<DocumentType> {

    private final DocumentTypeSelectionModel selectionModel;
    private final TickBoxCell tickBoxCell;

    public DocumentTypeCell(final DocumentTypeSelectionModel selectionModel) {
//        super("click");
        this.selectionModel = selectionModel;
        tickBoxCell = TickBoxCell.create(true, false);
    }

//    @Override
//    public void onBrowserEvent(final Context context,
//                               final Element parent,
//                               final DocumentType value,
//                               final NativeEvent event,
//                               final ValueUpdater<DocumentType> valueUpdater) {
//        if (value != null) {
//            super.onBrowserEvent(context, parent, value, event, valueUpdater);
//            final String type = event.getType();
//
//            final Element target = event.getEventTarget().cast();
//            if ("div".equalsIgnoreCase(target.getTagName())
//                    && "click".equals(type)
//                    && MouseUtil.isPrimary(event)) {
//
//                if (TickBoxAppearance.isTickBox(target)) {
//                    selectionModel.toggle(value);
//                }
//            }
//        }
//    }

    @Override
    public void render(final Context context, final DocumentType item, final SafeHtmlBuilder sb) {
        if (item != null) {
            final SafeHtml iconHtml = SvgImageUtil.toSafeHtml(item.getIcon(), "explorerCell-icon");

            final SafeHtml textHtml = Templates.div("explorerCell-text",
                    SafeHtmlUtils.fromString(item.getType()));

            final SafeHtmlBuilder content = new SafeHtmlBuilder();
            tickBoxCell.render(context, selectionModel.getState(item), content);
            content.append(iconHtml);
            content.append(textHtml);

            sb.append(Templates.div("explorerCell", content.toSafeHtml()));

            // Possibly a bit hacky as the <hr> is part of the selected item, so it looks a bit odd.
            if (TypeFilterPresenter.SELECT_ALL_OR_NONE_DOCUMENT_TYPE.equals(item)) {
                sb.appendHtmlConstant("<hr>");
            }
        }
    }
}
