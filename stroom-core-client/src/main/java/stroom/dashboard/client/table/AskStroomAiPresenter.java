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

package stroom.dashboard.client.table;

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.SearchModel;
import stroom.dashboard.shared.AskStroomAiRequest;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestFactory;
import stroom.entity.client.presenter.MarkdownConverter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class AskStroomAiPresenter extends MyPresenterWidget<AskStroomAiPresenter.AskStroomAiView>
        implements AskStroomAiUiHandlers {

    private static final String MARKDOWN_SECTION_BREAK = "\n\n---\n\n";
    private static final DashboardResource DASHBOARD_RESOURCE = GWT.create(DashboardResource.class);
    private final MarkdownConverter markdownConverter;
    private final RestFactory restFactory;
    private SearchModel searchModel;
    private DashboardSearchRequest dashboardSearchRequest;

    @Inject
    public AskStroomAiPresenter(final EventBus eventBus,
                                final AskStroomAiView view,
                                final MarkdownConverter markdownConverter,
                                final RestFactory restFactory) {
        super(eventBus, view);
        this.markdownConverter = markdownConverter;
        this.restFactory = restFactory;

        view.setUiHandlers(this);
    }

    @Override
    public void setSearchContext(final SearchModel searchModel,
                                 final DashboardSearchRequest request) {
        this.searchModel = searchModel;
        this.dashboardSearchRequest = request;
    }

    @Override
    public void onSendMessage(final String message) {
        getView().setSendButtonLoadingState(true);
        renderMarkdown("> " + message);

        // Scroll markdown container to bottom, so the user's message is displayed
        final Element markdownContainer = getView().getMarkdownContainer();
        markdownContainer.setScrollTop(markdownContainer.getScrollHeight());

        final AskStroomAiRequest request = new AskStroomAiRequest(dashboardSearchRequest, message);
        restFactory
                .create(DASHBOARD_RESOURCE)
                .method(res -> res.askStroomAi(
                        searchModel.getCurrentNode(),
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
    }
}
