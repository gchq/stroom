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

import stroom.svg.client.Icon;
import stroom.svg.shared.SvgImage;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.SpanElement;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.UIObject;
import com.google.gwt.user.client.ui.Widget;

/// A message in an Ask Stroom AI conversation shown as a `details` element, collapsed until its
/// summary is pressed (or Enter or Space pressed on it): the AI's thinking, data sent to it, a
/// request's detail, or what it is working on now. The summary is an icon and what the message is
/// (for progress, the progress itself, which changes as the work goes on); then the message, and
/// when it was sent.
public class AiDetailsMessage extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    HTMLPanel root;
    @UiField
    Icon icon;
    @UiField
    SpanElement summary;
    @UiField
    DivElement content;
    @UiField
    DivElement timestamp;

    /// Makes an empty message, until it is given its kind and content.
    public AiDetailsMessage() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Gives the message its look, icon and summary.
    ///
    /// @param kind What the message is.
    public void setKind(final Kind kind) {
        root.addStyleName(kind.className);
        icon.setImage(kind.icon);
        setSummary(kind.summary);
        // Progress has nothing to show but its summary
        UIObject.setVisible(content, kind != Kind.WORKING);
    }

    /// @param summary What the summary says, e.g. what the AI is working on.
    public void setSummary(final String summary) {
        this.summary.setInnerText(summary);
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


    // --------------------------------------------------------------------------------


    /// What a message shown as details is, which gives its look, icon and summary.
    public enum Kind {
        /// The AI's thinking.
        THINKING("ai-message--thinking", SvgImage.AI, "Thinking"),
        /// Data sent to the AI with a question.
        DATA("ai-message--data", SvgImage.TABLE, "Data context"),
        /// A request's detail, for debugging.
        DEBUG_DETAIL("ai-message--debug-detail", SvgImage.INFO, "Request detail"),
        /// What the AI is working on now, its summary the progress.
        WORKING("ai-message--working", SvgImage.INFO, "Working...");

        private final String className;
        private final SvgImage icon;
        private final String summary;

        Kind(final String className, final SvgImage icon, final String summary) {
            this.className = className;
            this.icon = icon;
            this.summary = summary;
        }
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AiDetailsMessage> {

    }
}
