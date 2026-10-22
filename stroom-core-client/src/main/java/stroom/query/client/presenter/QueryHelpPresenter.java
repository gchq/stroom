/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.presenter;

import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.item.client.SelectionList;
import stroom.query.client.presenter.QueryHelpPresenter.QueryHelpView;
import stroom.query.shared.CompletionsRequest.TextType;
import stroom.query.shared.InsertType;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.client.ClipboardUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.HasKeyboardSelectionPolicy.KeyboardSelectionPolicy;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;

import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public class QueryHelpPresenter
        extends MyPresenterWidget<QueryHelpView>
        implements QueryHelpUiHandlers {

    private final DynamicQueryHelpSelectionListModel model;
    private final QueryHelpAceCompletionProvider keyedAceCompletionProvider;
    private final QueryHelpDetailProvider detailProvider;
    private final MarkdownConverter markdownConverter;

    // The editor items are inserted into, once linked
    private EditorPresenter editorPresenter;

    @Inject
    public QueryHelpPresenter(final EventBus eventBus,
                              final QueryHelpView view,
                              final QueryHelpAceCompletionProvider keyedAceCompletionProvider,
                              final QueryHelpDetailProvider detailProvider,
                              final DynamicQueryHelpSelectionListModel model,
                              final MarkdownConverter markdownConverter,
                              final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        view.setUiHandlers(this);
        this.keyedAceCompletionProvider = keyedAceCompletionProvider;
        // Shows its requests' progress as this presenter does (its context's)
        keyedAceCompletionProvider.setTaskMonitorFactory(this);
        this.detailProvider = detailProvider;
        // Shows its requests' progress as this presenter does (its context's)
        detailProvider.setTaskMonitorFactory(this);
        this.model = model;
        // Shows its requests' progress as this presenter does (its context's)
        model.setTaskMonitorFactory(this);
        this.markdownConverter = markdownConverter;

        view.getSelectionList().setKeyboardSelectionPolicy(KeyboardSelectionPolicy.BOUND_TO_SELECTION);
        view.getSelectionList().init(model);

        // Set up the tooltip for the quickfilter
        uiConfigCache.get(uiConfig ->
                NullSafe.consume(uiConfig, ExtendedUiConfig::getHelpUrl, helpUrl ->
                        view.registerPopupTextProvider(() ->
                                QuickFilterTooltipUtil.createTooltip("Field Quick Filter", helpUrl))));
    }

    @Override
    protected void onBind() {
        super.onBind();
        final MultiSelectionModel<QueryHelpSelectionItem> selectionModel =
                getView().getSelectionList().getSelectionModel();
        registerHandler(selectionModel.addSelectionHandler(e -> {
            if (e.getSelectionType().isDoubleSelect()) {
                onInsert();
            } else {
                updateDetails();
            }
        }));
    }

    public AceCompletionProvider getKeyedAceCompletionProvider() {
        return keyedAceCompletionProvider;
    }

    private QueryHelpRow getSelectedItem() {
        final QueryHelpSelectionItem selectionItem = getView().getSelectionList().getSelectionModel().getSelected();
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getQueryHelpRow();
    }

    private void updateDetails() {
        getView().setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
        getView().enableButtons(false);
        final QueryHelpRow row = getSelectedItem();
        if (row != null) {
            detailProvider.getDetail(row, detail -> {
                if (detail != null) {
                    final SafeHtml markDownSafeHtml = markdownConverter.convertMarkdownToHtmlInFrame(
                            detail.getDocumentation());
                    getView().setDetails(markDownSafeHtml);
                    getView().enableButtons(detail.getInsertType().isInsertable());
                }
            });
        }
    }

    public void refresh() {
        getView().getSelectionList().refresh(false, false);
    }

    @Override
    public void onCopy() {
        final QueryHelpRow row = getSelectedItem();
        if (row != null) {
            detailProvider.getDetail(row, detail -> {
                if (detail != null &&
                    detail.getInsertType() != null &&
                    detail.getInsertType().isInsertable() &&
                    detail.getInsertText() != null) {
                    ClipboardUtil.copy(detail.getInsertText());
                }
            });
        }
    }

    @Override
    public void onInsert() {
        final QueryHelpRow row = getSelectedItem();
        if (row != null) {
            detailProvider.getDetail(row, detail -> {
                if (detail != null &&
                    detail.getInsertType() != null &&
                    detail.getInsertType().isInsertable() &&
                    detail.getInsertText() != null) {
                    insert(detail.getInsertText(), detail.getInsertType());
                }
            });
        }
    }

    public void setQuery(final String query) {
        model.setQuery(query);
        refresh();
    }

    private void insert(final String text, final InsertType insertType) {
        if (editorPresenter != null) {
            if (InsertType.SNIPPET.equals(insertType)) {
                editorPresenter.insertSnippet(text);
                editorPresenter.focus();
            } else if (insertType.isInsertable()) {
                editorPresenter.insertTextAtCursor(text);
                editorPresenter.focus();
            }
        }
    }

    /**
     * Associate this {@link QueryHelpPresenter} with an editor. The editor's will be set to use
     * this {@link QueryHelpPresenter} as its completion provider and items inserted using the
     * query help menu will be inserted into the editor.
     */
    public void linkToEditor(final EditorPresenter editorPresenter) {
        Objects.requireNonNull(editorPresenter);

        // Items inserted from the help go into it
        this.editorPresenter = editorPresenter;

        // This glues the editor code completion to the QueryHelpPresenter's completion provider
        // Need to do this via addAttachHandler so the editor is fully loaded
        // else it moans about the id not being a thing on the AceEditor
        editorPresenter.getWidget().addAttachHandler(event -> {
            // On attach only (it once also registered on every detach)
            if (event.isAttached()) {
                editorPresenter.registerCompletionProviders(getKeyedAceCompletionProvider());
            }
        });
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        model.setDataSourceRef(dataSourceRef);
        keyedAceCompletionProvider.setDataSourceRef(dataSourceRef);
    }

    public void setIncludedTypes(final Set<QueryHelpType> includedTypes) {
        model.setIncludedTypes(includedTypes);
        keyedAceCompletionProvider.setIncludedTypes(includedTypes);
    }

    public void setTextType(final TextType textType) {
        keyedAceCompletionProvider.setTextType(textType);
    }


    // --------------------------------------------------------------------------------


    public interface QueryHelpView extends View, HasUiHandlers<QueryHelpUiHandlers> {

        SelectionList<QueryHelpRow, QueryHelpSelectionItem> getSelectionList();

        void setDetails(SafeHtml details);

        void enableButtons(boolean enable);

        void registerPopupTextProvider(Supplier<SafeHtml> popupTextSupplier);
    }
}
