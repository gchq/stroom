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

package stroom.entity.client.component;

import stroom.data.client.CopyTextUtil;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/// The Info dialog's content: a document's details, tags and audit history.
public class InfoDocument extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Html info;

    private Consumer<NativeEvent> onMouseDown = event -> {
    };

    /// Makes empty content.
    public InfoDocument() {
        initWidget(BINDER.createAndBindUi(this));
        addDomHandler(event -> onMouseDown.accept(event.getNativeEvent()), MouseDownEvent.getType());
    }

    /// Shows a document's details: its UUID, type and name, its tags, and its audit history, each
    /// value one that a press copies.
    ///
    /// @param docRef       The document.
    /// @param tags         Its tags, or null or empty for none.
    /// @param auditEntries Its audit history, or null or empty for none.
    public void setInfo(final DocRef docRef,
                        final Collection<String> tags,
                        final List<AuditEntry> auditEntries) {
        final HtmlBuilder hb = new HtmlBuilder();
        appendLine("UUID", docRef.getUuid(), hb);
        appendLine("Type", docRef.getType(), hb);
        appendLine("Name", docRef.getName(), hb);

        if (NullSafe.hasItems(tags)) {
            appendLine("Tags", "", hb);
            tags.stream()
                    .sorted()
                    .forEach(tag -> appendLine("\t", tag, hb));
        }

        if (NullSafe.hasItems(auditEntries)) {
            hb.div(d -> appendKey("Audit Info", d), Attribute.className("info-line"));
            final TableBuilder tableBuilder = new TableBuilder();
            tableBuilder.row("Time", "User", "Action");
            for (final AuditEntry entry : auditEntries) {
                tableBuilder.row(
                        CopyTextUtil.render(entry.time(), false),
                        CopyTextUtil.render(entry.user(), false),
                        CopyTextUtil.render(entry.action(), false));
            }
            tableBuilder.write(hb);
        }

        info.setHTML(hb.toSafeHtml());
    }

    /// @param onMouseDown Told of a press on the details, e.g. to copy what was pressed.
    public void setOnMouseDown(final Consumer<NativeEvent> onMouseDown) {
        this.onMouseDown = onMouseDown;
    }

    private static void appendLine(final String key, final String value, final HtmlBuilder hb) {
        hb.div(d -> {
            appendKey(key, d);
            d.append(CopyTextUtil.render(value, false));
        }, Attribute.className("info-line"));
    }

    private static void appendKey(final String key, final HtmlBuilder hb) {
        hb.bold(key);
        if (!NullSafe.isBlankString(key)) {
            hb.append(": ");
        }
    }


    // --------------------------------------------------------------------------------


    /// An entry in a document's audit history, as shown.
    ///
    /// @param time   When it happened, formatted.
    /// @param user   Who did it.
    /// @param action What they did.
    public record AuditEntry(String time, String user, String action) {

    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, InfoDocument> {

    }
}
