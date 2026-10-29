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

package stroom.entity.client.component;

import stroom.data.client.CopyTextUtil;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocAuditEntry;
import stroom.document.client.event.ShowInfoDocumentDialogEvent;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeInfo;
import stroom.preferences.client.DateTimeFormatter;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;

/// A document's Info dialog ([InfoDocument]), made for one use. It isn't modal, so Info on another
/// document while it is shown shows that document in it ([#read]).
public class InfoDocumentController extends Controller {

    private final InfoDocument view;
    private final DateTimeFormatter dateTimeFormatter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param dateTimeFormatter Formats the audit entries' times.
    @Inject
    public InfoDocumentController(final EventBus eventBus,
                                  final InfoDocument view,
                                  final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);
        view.setOnMouseDown(event -> CopyTextUtil.onClick(event, this));
    }

    /// Shows a document's details in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener What shows it.
    /// @param event  The document's details, as the explorer's Info gives them.
    public void show(final HasControllerNode opener, final ShowInfoDocumentDialogEvent event) {
        read(event);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(500, 500))
                .caption("Info")
                .fire();
    }

    /// Shows another document's details in the dialog.
    ///
    /// @param event The document's details, as the explorer's Info gives them.
    public void read(final ShowInfoDocumentDialogEvent event) {
        final ExplorerNodeInfo explorerNodeInfo = event.getExplorerNodeInfo();
        final ResultPage<DocAuditEntry> auditEntryResultPage = event.getAuditEntries();
        final DocRef docRef = explorerNodeInfo.getExplorerNode().getDocRef();
        final ExplorerNode explorerNode = event.getExplorerNode();

        final HtmlBuilder hb = new HtmlBuilder();
        appendLine("UUID", docRef.getUuid(), hb);
        appendLine("Type", docRef.getType(), hb);
        appendLine("Name", docRef.getName(), hb);

        if (NullSafe.hasItems(explorerNode.getTags())) {
            appendLine("Tags", "", hb);
            final Set<String> tags = explorerNode.getTags();
            tags.stream()
                    .sorted()
                    .forEach(tag -> appendLine("\t", tag, hb));
        }

        // Add audit information
        if (auditEntryResultPage != null && !NullSafe.isEmptyCollection(auditEntryResultPage.getValues())) {
            hb.div(d -> appendKey("Audit Info", d), Attribute.className("info-line"));
            final TableBuilder tableBuilder = new TableBuilder();
            tableBuilder.row("Time", "User", "Action");
            for (final DocAuditEntry entry : auditEntryResultPage.getValues()) {
                final String time = dateTimeFormatter.format(entry.getTime());
                tableBuilder.row(
                        CopyTextUtil.render(time, false),
                        CopyTextUtil.render(entry.getUser().getDisplayName(), false),
                        CopyTextUtil.render(entry.getAction().getDisplayValue(), false)
                );
            }
            tableBuilder.write(hb);
        }

        view.setInfo(hb.toSafeHtml());
    }

    private void appendLine(final String key, final String value, final HtmlBuilder hb) {
        hb.div(d -> {
            appendKey(key, d);
            d.append(CopyTextUtil.render(value, false));
        }, Attribute.className("info-line"));
    }

    private void appendKey(final String key, final HtmlBuilder hb) {
        hb.bold(key);
        if (!NullSafe.isBlankString(key)) {
            hb.append(": ");
        }
    }
}
