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
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WordListPresenter extends MyPresenterWidget<PagerView> implements Refreshable {

    private static final String FIELD_WORD = "Word";
    private static final String FIELD_SOURCE_NAME = "SourceName";
    private static final String FIELD_TYPE = "Types";
    private static final WordListResource WORD_LIST_RESOURCE = GWT.create(WordListResource.class);

    private final RestFactory restFactory;
    private final MyDataGrid<Word> dataGrid;
    private final MultiSelectionModelImpl<Word> selectionModel;
    private WordList wordList = WordList.EMPTY;
    // The docRef of the dictionary we are presenting
    private DocRef docRef = null;
    private CriteriaFieldSort fieldSort = null;

    private final Comparator<Word> wordComparator = Comparator.nullsFirst(Comparator.comparing(
            Word::getWord, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
    private final Comparator<Word> sourceNameComparator = Comparator.nullsFirst(Comparator.comparing(
            this::mapWordToSourceName, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));
    private final Comparator<Word> typeComparator = Comparator.nullsFirst(Comparator.comparing(
            this::mapWordToImportType, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)));

    @Inject
    public WordListPresenter(final EventBus eventBus,
                             final PagerView view,
                             final RestFactory restFactory) {
        super(eventBus, view);

        this.restFactory = restFactory;
        dataGrid = new MyDataGrid<>(this);
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
                300);

        // Type
        final Column<Word, String> typeColumn = DataGridUtil.textColumnBuilder(this::mapWordToImportType)
                .withSorting(FIELD_TYPE)
                .build();
        dataGrid.addResizableColumn(
                typeColumn,
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("Whether the word is local to this dictionary or imported from another.")
                        .build(),
                80);

        // Primary Source
        final Column<Word, CommandLink> nodeNameColumn = DataGridUtil.commandLinkColumnBuilder(
                        buildOpenDocCommandLink())
                .withSorting(FIELD_SOURCE_NAME)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(nodeNameColumn);
        dataGrid.addResizableColumn(
                nodeNameColumn,
                DataGridUtil.headingBuilder("Source Dictionary")
                        .withToolTip("The dictionary that contains the word.")
                        .build(),
                300);

        // Additional sources
        final Column<Word, String> additionalSourcesColumn = DataGridUtil.textColumnBuilder(
                        this::wordToAdditionalSourcesStr)
                .build();
        dataGrid.addResizableColumn(
                additionalSourcesColumn,
                DataGridUtil.headingBuilder("Additional Source Dictionaries")
                        .withToolTip("Additional Dictionaries that also contain the word.")
                        .build(),
                400);

        dataGrid.addEndColumn(new EndColumn<>());

        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                //noinspection PatternVariableCanBeUsed // cos GWT
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();

                fieldSort = new CriteriaFieldSort(
                        orderByColumn.getField(),
                        !event.isSortAscending(),
                        orderByColumn.isIgnoreCase());
                updateGrid();
            }
        });
    }

    private String mapWordToImportType(final Word word) {
        return Objects.equals(getDocRef(), wordList.getSource(word).orElse(null))
                ? "Local"
                : "Imported";
    }

    private String wordToAdditionalSourcesStr(final Word word) {
        if (word == null || NullSafe.isEmptyCollection(word.getAdditionalSourceUuids())) {
            return null;
        } else {
            return word.getAdditionalSourceUuids()
                    .stream()
                    .map(wordList::getSource)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(DocRef::getName)
                    .collect(Collectors.joining(", "));
        }
    }

    private Function<Word, CommandLink> buildOpenDocCommandLink() {
        return (Word word) ->
                NullSafe.get(
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
            words = wordList.getWordList();
        } else {
            final String sortId = fieldSort.getId();
            final boolean isDescending = fieldSort.isDesc();
            final Comparator<Word> comparator;
            if (FIELD_WORD.equals(sortId)) {
                comparator = buildComparator(isDescending, wordComparator, sourceNameComparator);
            } else if (FIELD_SOURCE_NAME.equals(sortId)) {
                comparator = buildComparator(isDescending, sourceNameComparator, wordComparator);
            } else if (FIELD_TYPE.equals(sortId)) {
                comparator = buildComparator(isDescending, typeComparator, sourceNameComparator, wordComparator);
            } else {
                comparator = null;
            }
            Stream<Word> wordStream = wordList.getWordList()
                    .stream();

            if (comparator != null) {
                wordStream = wordStream.sorted(comparator);
            }
            words = wordStream.collect(Collectors.toList());
        }

        dataGrid.setRowData(0, words);
        dataGrid.setRowCount(wordList.size());
        dataGrid.redraw();
    }

    private String mapWordToSourceName(final Word word) {
        return getWordList().getSource(word)
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

    private Comparator<Word> buildComparator(final boolean isDescending,
                                             final Comparator<Word> primaryComparator,
                                             final Comparator<Word>... subsequentComparators) {
        Objects.requireNonNull(primaryComparator);
        Comparator<Word> comparator = primaryComparator;
        if (isDescending) {
            comparator = comparator.reversed();
        }

        for (final Comparator<Word> subsequentComparator : NullSafe.asList(subsequentComparators)) {
            if (subsequentComparator != null) {
                comparator = comparator.thenComparing(comparator);
            }
        }

        return Comparator.nullsFirst(comparator);
    }
}
