/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client.component;

import stroom.ai.shared.AiAttachmentStatus;
import stroom.svg.client.Icon;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.util.client.Clipboard;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;

/// A message in an Ask Stroom AI conversation (`ai-message`): the user's question, a reply, an
/// error, or data attached to the conversation. A reply can be copied, and the user's own messages
/// and attachments deleted (the Delete button shows while the pointer or the focus is on the
/// message). An attachment shows its View data button, its description and how its download is
/// going. The time it was sent is at its foot, the full time in its tooltip.
///
/// A message is shown as one of the [Kind]s; one shown as details (thinking, data, request detail,
/// progress), collapsed until opened, is an [AiDetailsMessage].
public class AiMessage extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final Templates TEMPLATES = GWT.create(Templates.class);

    private static final String COPY_LABEL = "Copy";
    private static final String COPIED_LABEL = "Copied";
    // How long Copy says Copied once it has copied the message
    private static final int COPIED_MILLIS = 2000;

    @UiField
    HTMLPanel root;
    @UiField
    DivElement header;
    @UiField
    Toolbar actions;
    @UiField
    ToolbarButton viewData;
    @UiField
    SpanElement description;
    @UiField
    DivElement status;
    @UiField
    Icon statusIcon;
    @UiField
    DivElement statusText;
    @UiField
    DivElement content;
    @UiField
    ToolbarButton delete;
    @UiField
    Button copy;
    @UiField
    DivElement timestamp;

    private String copyText;
    private Runnable onDelete = () -> {
    };
    private Runnable onViewData = () -> {
    };

    /// Makes an empty message, of no kind, that can't be copied or deleted, until it is given its
    /// kind and content.
    public AiMessage() {
        initWidget(BINDER.createAndBindUi(this));
        UIObject.setVisible(header, false);
        UIObject.setVisible(status, false);
        actions.setVisible(false);
        copy.setVisible(false);
        copy.setOnClick(this::copy);
        delete.setOnClick(() -> onDelete.run());
        viewData.setOnClick(() -> onViewData.run());
    }

    /// @param kind What the message is, which gives its look.
    public void setKind(final Kind kind) {
        if (kind.className != null) {
            root.addStyleName(kind.className);
        }
    }

    /// @param content The message, rendered (e.g. from markdown).
    public void setContent(final SafeHtml content) {
        this.content.setInnerSafeHtml(content);
    }

    /// Shows when the message was sent.
    ///
    /// @param time     When, e.g. "5 minutes ago".
    /// @param fullTime When, in full, shown as its tooltip.
    public void setTime(final String time, final String fullTime) {
        timestamp.setInnerText(time);
        timestamp.setTitle(fullTime);
    }

    /// Shows Copy, which copies the message as the user would type it (e.g. its markdown).
    ///
    /// @param copyText What Copy copies.
    public void setCopyText(final String copyText) {
        this.copyText = copyText;
        copy.setContent(copyContent(SvgImage.COPY, COPY_LABEL));
        copy.setVisible(true);
    }

    /// Shows Delete, named for what it deletes.
    ///
    /// @param name What Delete says it does, e.g. "Delete message".
    public void setDeletable(final String name) {
        delete.setTitle(name);
        actions.setVisible(true);
    }

    /// Shows the message as an attachment: its View data button, its description, and its download
    /// status, which starts as downloading.
    ///
    /// @param description What is attached, e.g. a table's name.
    /// @param hasStatus   Whether it has a download whose status is shown.
    public void setAttachment(final String description, final boolean hasStatus) {
        this.description.setInnerText(description);
        viewData.setTitle("View data");
        UIObject.setVisible(header, true);
        UIObject.setVisible(status, hasStatus);
        UIObject.setVisible(content, false);
    }

    /// Shows how an attachment's download is going.
    ///
    /// @param attachmentStatus Where it has got to, which gives its icon.
    /// @param text             What it says, e.g. "Ready --- 42 rows".
    public void setAttachmentStatus(final AiAttachmentStatus attachmentStatus, final String text) {
        statusIcon.setImage(switch (attachmentStatus) {
            case PENDING, DOWNLOADING -> SvgImage.DOWNLOAD;
            case READY -> SvgImage.OK;
            case ERROR -> SvgImage.ERROR;
        });
        statusText.setInnerText(text);
    }

    /// @param onDelete Run when the user presses Delete.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = Objects.requireNonNull(onDelete, "onDelete");
    }

    /// @param onViewData Run when the user presses an attachment's View data.
    public void setOnViewData(final Runnable onViewData) {
        this.onViewData = Objects.requireNonNull(onViewData, "onViewData");
    }

    // Copies the message, and says Copied for a moment
    private void copy() {
        if (NullSafe.isBlankString(copyText)) {
            return;
        }
        Clipboard.writeText(copyText, () -> {
            copy.setContent(copyContent(SvgImage.OK, COPIED_LABEL));
            new Timer() {
                @Override
                public void run() {
                    copy.setContent(copyContent(SvgImage.COPY, COPY_LABEL));
                }
            }.schedule(COPIED_MILLIS);
        }, () -> {
        });
    }

    private static SafeHtml copyContent(final SvgImage icon, final String label) {
        return TEMPLATES.copy(SvgImageUtil.toSafeHtml(icon, "icon-wrap"), label);
    }


    // --------------------------------------------------------------------------------


    /// What a message is, which gives its look.
    public enum Kind {
        /// The user's question.
        USER("ai-message--user"),
        /// The AI's reply.
        ASSISTANT("ai-message--assistant"),
        /// What went wrong.
        ERROR("ai-message--error"),
        /// Data attached to the conversation.
        ATTACHMENT("ai-message--data"),
        /// Anything else.
        OTHER(null);

        private final String className;

        Kind(final String className) {
            this.className = className;
        }
    }


    // --------------------------------------------------------------------------------


    interface Templates extends SafeHtmlTemplates {

        // Copy's icon and label, as its own content
        @Template("{0}<span>{1}</span>")
        SafeHtml copy(SafeHtml icon, String label);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AiMessage> {

    }
}
