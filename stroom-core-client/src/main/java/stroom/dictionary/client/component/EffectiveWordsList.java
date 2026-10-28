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

package stroom.dictionary.client.component;

import stroom.cell.info.client.CommandLink;
import stroom.dictionary.shared.Word;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.OrderByColumn;
import stroom.widget.datagrid.client.PagerPanel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/// The words a dictionary has once its imports are added (the dictionary's own and those of the
/// dictionaries it imports, however deep), a row each with where it comes from, in the order the
/// user sorts them by. Read only.
public class EffectiveWordsList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String FIELD_WORD = "Word";
    private static final String FIELD_SOURCE_NAME = "SourceName";
    private static final String FIELD_TYPE = "Types";

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<Word> dataGrid;

    private WordList wordList = WordList.EMPTY;
    private DocRef docRef;
    private CriteriaFieldSort fieldSort;
    private Consumer<DocRef> onOpen = source -> {
    };

    /// Makes an empty list.
    public EffectiveWordsList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by word, so a row whose values change is still the same row
        dataGrid.setKeyProvider(Word::getWord);
        dataGrid.addDefaultSelectionModel(false);
        addColumns();
    }

    /// @param docRef The dictionary whose words are shown, to name the list by and to tell its own
    ///               words from those imported; or `null` for none.
    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
        if (docRef != null && docRef.getName() != null) {
            dataGrid.setTableName("Dictionary '" + docRef.getName() + "' Words");
        }
    }

    /// Shows a dictionary's words, in the order the user chose.
    ///
    /// @param wordList The words.
    public void setWordList(final WordList wordList) {
        this.wordList = wordList;
        showWords();
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @param onRefresh Run when the pager's refresh button is pressed.
    public void setOnRefresh(final Runnable onRefresh) {
        pager.getRefreshButton().setOnClick(onRefresh);
    }

    /// @param onOpen Given a source dictionary to open, when its link is pressed.
    public void setOnOpen(final Consumer<DocRef> onOpen) {
        this.onOpen = onOpen;
    }

    private void addColumns() {
        final Column<Word, String> wordColumn = DataGridUtil.textColumnBuilder(Word::getWord)
                .withSorting(FIELD_WORD)
                .build();
        dataGrid.addAutoResizableColumn(
                wordColumn,
                DataGridUtil.headingBuilder("Word")
                        .withToolTip("The Dictionary word or line.")
                        .build(),
                300);

        final Column<Word, String> typeColumn = DataGridUtil.textColumnBuilder(this::getImportType)
                .withSorting(FIELD_TYPE)
                .build();
        dataGrid.addResizableColumn(
                typeColumn,
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("Whether the word is local to this dictionary or imported from another.")
                        .build(),
                80);

        final Column<Word, CommandLink> sourceColumn = DataGridUtil.commandLinkColumnBuilder(this::getSourceLink)
                .withSorting(FIELD_SOURCE_NAME)
                .build();
        DataGridUtil.addCommandLinkFieldUpdater(sourceColumn);
        dataGrid.addResizableColumn(
                sourceColumn,
                DataGridUtil.headingBuilder("Source Dictionary")
                        .withToolTip("The dictionary that contains the word.")
                        .build(),
                300);

        final Column<Word, String> additionalSourcesColumn = DataGridUtil.textColumnBuilder(
                        this::getAdditionalSourceNames)
                .build();
        dataGrid.addResizableColumn(
                additionalSourcesColumn,
                DataGridUtil.headingBuilder("Additional Source Dictionaries")
                        .withToolTip("Additional Dictionaries that also contain the word.")
                        .build(),
                400);

        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof final OrderByColumn<?, ?> orderByColumn) {
                fieldSort = new CriteriaFieldSort(
                        orderByColumn.getField(),
                        !event.isSortAscending(),
                        orderByColumn.isIgnoreCase());
                showWords();
            }
        });
    }

    private void showWords() {
        final List<Word> words;
        final Comparator<Word> comparator = getComparator();
        if (comparator == null) {
            words = wordList.getWordList();
        } else {
            words = wordList.getWordList()
                    .stream()
                    .sorted(comparator)
                    .collect(Collectors.toList());
        }
        dataGrid.setRowData(0, words);
        dataGrid.setRowCount(wordList.size());
        dataGrid.redraw();
    }

    // The order the user chose, with ties broken by the other columns
    private Comparator<Word> getComparator() {
        if (fieldSort == null) {
            return null;
        }
        return comparator(fieldSort.getId(), fieldSort.isDesc(), this::getSourceName, this::getImportType);
    }

    /// The order of the words for a sort on one column, with ties broken by the other columns: by
    /// word then source, by source then word, or by type then source then word.
    ///
    /// @param sortId     The column sorted on: `Word`, `SourceName` or `Types`.
    /// @param desc       Whether in descending order.
    /// @param sourceName A word's source's name.
    /// @param type       A word's type, local or imported.
    /// @return The order, or null for a column that isn't sorted on.
    static Comparator<Word> comparator(final String sortId,
                                       final boolean desc,
                                       final Function<Word, String> sourceName,
                                       final Function<Word, String> type) {
        final Comparator<Word> byWord = textOrder(Word::getWord);
        final Comparator<Word> bySourceName = textOrder(sourceName);
        final Comparator<Word> byType = textOrder(type);
        final Comparator<Word> comparator;
        if (FIELD_WORD.equals(sortId)) {
            comparator = byWord.thenComparing(bySourceName);
        } else if (FIELD_SOURCE_NAME.equals(sortId)) {
            comparator = bySourceName.thenComparing(byWord);
        } else if (FIELD_TYPE.equals(sortId)) {
            comparator = byType.thenComparing(bySourceName).thenComparing(byWord);
        } else {
            return null;
        }
        return Comparator.nullsFirst(desc
                ? comparator.reversed()
                : comparator);
    }

    private static Comparator<Word> textOrder(final Function<Word, String> text) {
        return Comparator.comparing(text, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
    }

    private String getImportType(final Word word) {
        return Objects.equals(docRef, wordList.getSource(word).orElse(null))
                ? "Local"
                : "Imported";
    }

    private String getSourceName(final Word word) {
        return wordList.getSource(word)
                .map(DocRef::getName)
                .orElse(null);
    }

    private CommandLink getSourceLink(final Word word) {
        if (word == null) {
            return null;
        }
        return wordList.getSource(word)
                .map(source -> new CommandLink(
                        source.getName(),
                        "Open " + source.getType() + " '" + source.getName() + "'.",
                        () -> onOpen.accept(source)))
                .orElse(null);
    }

    private String getAdditionalSourceNames(final Word word) {
        if (word == null || NullSafe.isEmptyCollection(word.getAdditionalSourceUuids())) {
            return null;
        }
        return word.getAdditionalSourceUuids()
                .stream()
                .map(wordList::getSource)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(DocRef::getName)
                .collect(Collectors.joining(", "));
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, EffectiveWordsList> {

    }
}
