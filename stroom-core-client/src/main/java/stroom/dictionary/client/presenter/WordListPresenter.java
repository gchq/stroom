/*
 * Copyright 2024 Crown Copyright
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

package stroom.dictionary.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.cell.info.client.CommandLink;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.data.table.client.Refreshable;
import stroom.dictionary.shared.Word;
import stroom.dictionary.shared.WordList;
import stroom.dictionary.shared.WordListResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.GwtNullSafe;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WordListPresenter extends MyPresenterWidget<PagerView> implements Refreshable {

    private static final String FIELD_WORD = "Word";
    private static final String FIELD_SOURCE_NAME = "SourceName";

    private static final WordListResource WORD_LIST_RESOURCE = GWT.create(WordListResource.class);

    private final RestFactory restFactory;
    private final MyDataGrid<Word> dataGrid;
    private final MultiSelectionModelImpl<Word> selectionModel;
    private WordList wordList = WordList.EMPTY;
    // The docRef of the dictionary we are presenting
    private DocRef docRef = null;
    private CriteriaFieldSort fieldSort = null;

    @Inject
    public WordListPresenter(final EventBus eventBus,
                             final PagerView view,
                             final RestFactory restFactory) {
        super(eventBus, view);

        this.restFactory = restFactory;
        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        initTableColumns();

        registerHandler(view.getRefreshButton().addClickHandler(event -> {
            refresh();
        }));
    }

    private void fetchData() {
        final DocRef docRef = getDocRef();
        if (docRef != null) {
            GWT.log("Fetching data for " + docRef);
            restFactory
                    .create(WORD_LIST_RESOURCE)
                    .method((WordListResource resource) ->
                            resource.getWords(docRef.getUuid()))
                    .onSuccess(this::setData)
                    .onFailure(error ->
                            AlertEvent.fireError(
                                    WordListPresenter.this,
                                    error.getMessage(),
                                    null))
                    .taskMonitorFactory(getView())
                    .exec();
        } else {
            setData(WordList.EMPTY);
        }
    }

    /**
     * Add the columns to the table.
     */
    private void initTableColumns() {
        // Word
        final Column<Word, String> wordColumn = DataGridUtil.textColumnBuilder(Word::getWord)
                .withSorting(FIELD_WORD)
                .build();
        dataGrid.addAutoResizableColumn(
                wordColumn,
                DataGridUtil.headingBuilder("Word")
                        .withToolTip("The Dictionary word or line.")
                        .build(),
                500);

        // Source
        final Column<Word, CommandLink> nodeNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        buildOpenDocCommandLink())
                .withSorting(FIELD_SOURCE_NAME)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(nodeNameColumn);
        dataGrid.addResizableColumn(
                nodeNameColumn,
                DataGridUtil.headingBuilder("Source Dictionary")
                        .withToolTip("The Dictionary that contains the word.")
                        .build(),
                500);

        dataGrid.addEndColumn(new EndColumn<>());

        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();

                fieldSort = new CriteriaFieldSort(
                        orderByColumn.getField(),
                        !event.isSortAscending(),
                        orderByColumn.isIgnoreCase());
                updateGrid();
            }
        });
    }

    private Function<Word, CommandLink> buildOpenDocCommandLink() {
        return (Word word) ->
                GwtNullSafe.get(
                                word,
                                w -> getWordList().getSource(word))
                        .map(sourceRef -> {
                            final String sourceName = sourceRef.getName();
                            return new CommandLink(
                                    sourceName,
                                    "Open " + sourceRef.getType() + " '" + sourceName + "'.",
                                    () ->
                                            OpenDocumentEvent.fire(
                                                    WordListPresenter.this, sourceRef, true));

                        })
                        .orElse(null);
    }

    private void setData(final WordList wordList) {
        this.wordList = wordList;
        updateGrid();
    }

    private void updateGrid() {
        final List<Word> words;
        if (fieldSort == null) {
            words = wordList.getSortedList();
        } else {
            final String sortId = fieldSort.getId();
            Comparator<Word> comparator;
            if (FIELD_WORD.equals(sortId)) {
                comparator = Comparator.nullsFirst(Comparator.comparing(
                                Word::getWord,
                                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Word::getSourceUuid));
            } else if (FIELD_SOURCE_NAME.equals(sortId)) {
                comparator = Comparator.nullsFirst(Comparator.comparing(
                                this::getSourceName,
                                Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(Word::getWord));
            } else {
                comparator = Word.CASE_INSENSE_WORD_COMPARATOR;
            }
            if (fieldSort.isDesc()) {
                comparator = comparator.reversed();
            }
            words = wordList.getWordList()
                    .stream()
                    .sorted(comparator)
                    .collect(Collectors.toList());
        }

        dataGrid.setRowData(0, words);
        dataGrid.setRowCount(wordList.size());
        dataGrid.redraw();
    }

    private String getSourceName(final Word word) {
        return wordList.getSource(word)
                .map(DocRef::getName)
                .orElse(null);
    }

    public MultiSelectionModel<Word> getSelectionModel() {
        return selectionModel;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        fetchData();
    }

    private WordList getWordList() {
        return wordList;
    }

    private DocRef getDocRef() {
        return docRef;
    }

    public void refresh() {
        fetchData();
    }
}
