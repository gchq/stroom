/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AskStroomAiPresenter.AskStroomAiProxy;
import stroom.ai.client.AskStroomAiPresenter.AskStroomAiView;
import stroom.ai.shared.AskStroomAiData;
import stroom.ai.shared.AskStroomAiRequest;
import stroom.ai.shared.AskStroomAiResource;
import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.data.client.event.AskStroomAiEvent;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestFactory;
import stroom.docref.HasDisplayValue;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

public class AskStroomAiPresenter
        extends MyPresenter<AskStroomAiView, AskStroomAiProxy>
        implements AskStroomAiUiHandlers, AskStroomAiEvent.Handler {

    private static final String MARKDOWN_SECTION_BREAK = "\n\n---\n\n";
    private static final AskStroomAiResource RESOURCE = GWT.create(AskStroomAiResource.class);
    private final MarkdownConverter markdownConverter;
    private final RestFactory restFactory;
    private String node;
    private AskStroomAiData data;

    private boolean showing;

    @Inject
    public AskStroomAiPresenter(final EventBus eventBus,
                                final AskStroomAiView view,
                                final AskStroomAiProxy askStroomAiProxy,
                                final MarkdownConverter markdownConverter,
                                final RestFactory restFactory) {
        super(eventBus, view, askStroomAiProxy);
        this.markdownConverter = markdownConverter;
        this.restFactory = restFactory;

        view.setUiHandlers(this);
    }

    @Override
    protected void revealInParent() {

    }

    @ProxyEvent
    @Override
    public void onShow(final AskStroomAiEvent event) {
        if (!showing) {
            showing = true;

            ShowPopupEvent.builder(this)
                    .popupType(PopupType.CLOSE_DIALOG)
                    .popupSize(PopupSize.resizable(700, 500))
                    .caption("Ask Stroom AI")
                    .onShow(e -> {
                        setContext(event.getNode(),
                                event.getData());
                        getView().focus();
                    })
                    .onHide(e -> {
                        showing = false;
                    })
                    .fire();
        }
    }

    @Override
    public void onDockBehaviourChange(final DockBehaviour dockBehaviour) {

    }

    @ProxyCodeSplit
    public interface AskStroomAiProxy extends Proxy<AskStroomAiPresenter> {

    }

    @Override
    public void setContext(final String node, final AskStroomAiData data) {
        this.node = node;
        this.data = data;
    }

    @Override
    public void onSendMessage(final String message) {
        getView().setSendButtonLoadingState(true);
        renderMarkdown("> " + message);

        // Scroll markdown container to bottom, so the user's message is displayed
        final Element markdownContainer = getView().getMarkdownContainer();
        markdownContainer.setScrollTop(markdownContainer.getScrollHeight());

        final AskStroomAiRequest request = new AskStroomAiRequest(data, message);
        restFactory
                .create(RESOURCE)
                .method(res -> res.askStroomAi(
                        node,
                        request))
                .onSuccess(response -> {
                    onMessageReceived(response.getMessage());
                    getView().setSendButtonLoadingState(false);
                })
                .onFailure(error -> {
                    showError(error, "Stroom AI request failed", () -> {
                        getView().setSendButtonLoadingState(false);
                    });
                })
                .taskMonitorFactory(this)
                .exec();
    }

    private void showError(final RestError error, final String message, final AlertCallback callback) {
        AlertEvent.fireError(
                AskStroomAiPresenter.this,
                message + " - " + error.getMessage(),
                null,
                callback);
    }

    @Override
    public void clearHistory() {
        final Element markdownContainer = getView().getMarkdownContainer();
        markdownContainer.removeAllChildren();
    }

    private void onMessageReceived(final String message) {
        final Element markdownContainer = getView().getMarkdownContainer();
        final int oldScrollHeight = markdownContainer.getScrollHeight();

        renderMarkdown(message);

        // Scroll down a little, to display the start of the response message
        markdownContainer.setScrollTop(oldScrollHeight + 50);
    }

    private void renderMarkdown(final String message) {
        final Element markdownContainer = getView().getMarkdownContainer();

        // Emit the rendered markdown as HTML
        final StringBuilder sb = new StringBuilder();
        if (markdownContainer.hasChildNodes()) {
            sb.append(MARKDOWN_SECTION_BREAK);
        }
        sb.append(message);
        final SafeHtml markdownHtml = markdownConverter.convertMarkdownToHtml(sb.toString());
        final DivElement newMarkdownContent = Document.get().createDivElement();
        newMarkdownContent.setInnerSafeHtml(markdownHtml);

        // Append all new markdown nodes to the container
        while (newMarkdownContent.getFirstChild() != null) {
            markdownContainer.appendChild(newMarkdownContent.getFirstChild());
        }
    }

    public interface AskStroomAiView extends View, Focus, HasUiHandlers<AskStroomAiUiHandlers> {

        Element getMarkdownContainer();

        String getMessage();

        void setSendButtonLoadingState(final boolean enabled);

        void setDockBehaviour(final DockBehaviour dockBehaviour);

        DockBehaviour getDockBehaviour();
    }

    public static class DockBehaviour {

        private final DockType dockType;
        private final DockLocation dockLocation;

        public DockBehaviour(final DockType dockType,
                             final DockLocation dockLocation) {
            this.dockType = dockType;
            this.dockLocation = dockLocation;
        }

        public DockType getDockType() {
            return dockType;
        }

        public DockLocation getDockLocation() {
            return dockLocation;
        }
    }

    public enum DockType implements HasDisplayValue {
        DIALOG("Dialog"),
        TAB("Tab"),
        FLOAT("Float"),
        DOCK("Dock");

        private final String displayValue;

        DockType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public enum DockLocation implements HasDisplayValue {
        TOP("Top"),
        LEFT("Left"),
        BOTTOM("Bottom"),
        RIGHT("Right");

        private final String displayValue;

        DockLocation(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
