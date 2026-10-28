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

package stroom.query.client.component;

import stroom.docref.DocRef;
import stroom.editor.client.component.CodeEditorController;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.client.presenter.DynamicQueryHelpSelectionListModel;
import stroom.query.client.presenter.QueryHelpAceCompletionProvider;
import stroom.query.client.presenter.QueryHelpDetailProvider;
import stroom.query.client.presenter.QueryHelpSelectionItem;
import stroom.query.shared.CompletionsRequest.TextType;
import stroom.query.shared.InsertType;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.Clipboard;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;

import java.util.Objects;
import java.util.Set;

/// A query's help ([QueryHelp]): fills its contents from the server, shows the details of the item
/// selected, and inserts it into the editor it is linked to, or copies it.
public class QueryHelpController extends Controller {

    private final QueryHelp view;
    private final DynamicQueryHelpSelectionListModel model;
    private final QueryHelpAceCompletionProvider keyedAceCompletionProvider;
    private final QueryHelpDetailProvider detailProvider;
    private final MarkdownConverter markdownConverter;

    // The editor items are inserted into, once linked
    private CodeEditorController editor;

    /// Made by GIN, with its help.
    ///
    /// @param eventBus                   The application's event bus.
    /// @param view                       The help.
    /// @param keyedAceCompletionProvider Gives a linked editor its completions.
    /// @param detailProvider             Fetches an item's details.
    /// @param model                      Fetches the contents.
    /// @param markdownConverter          Renders an item's documentation.
    /// @param uiConfigCache              Gives the address of the quick filter's help.
    @Inject
    public QueryHelpController(final EventBus eventBus,
                               final QueryHelp view,
                               final QueryHelpAceCompletionProvider keyedAceCompletionProvider,
                               final QueryHelpDetailProvider detailProvider,
                               final DynamicQueryHelpSelectionListModel model,
                               final MarkdownConverter markdownConverter,
                               final UiConfigCache uiConfigCache) {
        super(eventBus);
        this.view = view;
        this.keyedAceCompletionProvider = keyedAceCompletionProvider;
        this.detailProvider = detailProvider;
        this.model = model;
        this.markdownConverter = markdownConverter;
        initWidget(view);

        // Its helpers show their requests' progress as this does (its context's, when they start)
        final TaskMonitorFactory taskMonitorFactory = () -> taskMonitor().createTaskMonitor();
        keyedAceCompletionProvider.setTaskMonitorFactory(taskMonitorFactory);
        detailProvider.setTaskMonitorFactory(taskMonitorFactory);
        model.setTaskMonitorFactory(taskMonitorFactory);

        view.getSelectionList().setModel(model);
        view.setOnCopy(this::onCopy);
        view.setOnInsert(this::onInsert);

        // Set up the tooltip for the quickfilter
        uiConfigCache.get(uiConfig ->
                NullSafe.consume(uiConfig, ExtendedUiConfig::getHelpUrl, helpUrl ->
                        view.setQuickFilterTooltip(() ->
                                QuickFilterTooltipUtil.createTooltip("Field Quick Filter", helpUrl))));

        final MultiSelectionModel<QueryHelpSelectionItem> selectionModel = view.getSelectionList().getSelectionModel();
        own(selectionModel.addSelectionHandler(e -> {
            if (e.getSelectionType().isDoubleSelect()) {
                onInsert();
            } else {
                updateDetails();
            }
        }));
    }

    /// @return The provider of completions from the help, for an editor.
    public AceCompletionProvider getKeyedAceCompletionProvider() {
        return keyedAceCompletionProvider;
    }

    /// Fetches the contents again.
    public void refresh() {
        view.getSelectionList().refresh(false, false);
    }

    /// @param query The query, whose data source and the like the contents follow.
    public void setQuery(final String query) {
        model.setQuery(query);
        refresh();
    }

    /// Links the help to an editor: the editor completes from the help, and items inserted from
    /// the help go into it.
    ///
    /// @param editor The editor's controller.
    public void linkToEditor(final CodeEditorController editor) {
        Objects.requireNonNull(editor);

        // Items inserted from the help go into it
        this.editor = editor;

        // This glues the editor code completion to the help's completion provider
        // Need to do this via addAttachHandler so the editor is fully loaded
        // else it moans about the id not being a thing on the AceEditor
        own(editor.getCodeEditor().addAttachHandler(event -> {
            // On attach only (it once also registered on every detach)
            if (event.isAttached()) {
                editor.registerCompletionProviders(getKeyedAceCompletionProvider());
            }
        }));
    }

    /// @param dataSourceRef The data source whose fields the help lists.
    public void setDataSourceRef(final DocRef dataSourceRef) {
        model.setDataSourceRef(dataSourceRef);
        keyedAceCompletionProvider.setDataSourceRef(dataSourceRef);
    }

    /// @param includedTypes The kinds of item the help lists, e.g. functions.
    public void setIncludedTypes(final Set<QueryHelpType> includedTypes) {
        model.setIncludedTypes(includedTypes);
        keyedAceCompletionProvider.setIncludedTypes(includedTypes);
    }

    /// @param textType The kind of text being completed, e.g. an expression.
    public void setTextType(final TextType textType) {
        keyedAceCompletionProvider.setTextType(textType);
    }

    private QueryHelpRow getSelectedItem() {
        final QueryHelpSelectionItem selectionItem = view.getSelectionList().getSelectionModel().getSelected();
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getQueryHelpRow();
    }

    private void updateDetails() {
        view.setDetails(SafeHtmlUtils.EMPTY_SAFE_HTML);
        view.setButtonsEnabled(false);
        final QueryHelpRow row = getSelectedItem();
        if (row != null) {
            detailProvider.getDetail(row, detail -> {
                if (detail != null) {
                    final SafeHtml markDownSafeHtml = markdownConverter.convertMarkdownToHtmlInFrame(
                            detail.getDocumentation());
                    view.setDetails(markDownSafeHtml);
                    view.setButtonsEnabled(detail.getInsertType().isInsertable());
                }
            });
        }
    }

    private void onCopy() {
        final QueryHelpRow row = getSelectedItem();
        if (row != null) {
            detailProvider.getDetail(row, detail -> {
                if (detail != null &&
                    detail.getInsertType() != null &&
                    detail.getInsertType().isInsertable() &&
                    detail.getInsertText() != null) {
                    Clipboard.writeText(detail.getInsertText());
                }
            });
        }
    }

    private void onInsert() {
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

    private void insert(final String text, final InsertType insertType) {
        if (editor != null) {
            if (InsertType.SNIPPET.equals(insertType)) {
                editor.insertSnippet(text);
                editor.focus();
            } else if (insertType.isInsertable()) {
                editor.insertTextAtCursor(text);
                editor.focus();
            }
        }
    }

}
