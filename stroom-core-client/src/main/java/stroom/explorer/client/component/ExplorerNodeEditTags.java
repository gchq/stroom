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

package stroom.explorer.client.component;

import stroom.docref.DocRef;
import stroom.item.client.SelectionList;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.regexp.shared.SplitResult;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// Edits a document's tags, or picks tags to add to several documents: tags are typed in (which
/// also filters the known tags) or moved from the list of all known tags to the document's.
///
/// Both lists are selection lists of several choices ([SelectionList#setMultiSelect]). Enter, or a
/// double click, on the known tags adds those chosen; Enter, a double click, Delete or Backspace on
/// the document's tags removes those chosen. Down in the field goes to the known tags, and Up from
/// their first goes back.
public class ExplorerNodeEditTags extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final RegExp TAG_SPLIT_REGEX = RegExp.compile(" +");
    private static final Predicate<String> ALWAYS_TRUE_PREDICATE = (str) -> true;

    private final KeyPressHandler tagsKeyPressHandler = new TagsKeyPressHandler();
    private final TagListModel allTagsModel = new TagListModel();
    private final TagListModel nodeTagsModel = new TagListModel();

    private final Set<String> nodeTags = new HashSet<>();
    private final Set<String> allTags = new HashSet<>();

    @UiField
    TextBox textBox;
    @UiField
    ToolbarButton clearInputButton;
    @UiField
    ToolbarButton addFromInputButton;
    @UiField
    FormGroup allTagsFormGroup;
    @UiField
    SelectionList<String, SimpleSelectionItemWrapper<String>> allTagsList;
    @UiField
    ToolbarButton addFromAllTagsButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    FormGroup nodeTagsFormGroup;
    @UiField
    SelectionList<String, SimpleSelectionItemWrapper<String>> nodeTagsList;
    private List<DocRef> docRefs;

    /// Makes an empty editor.
    public ExplorerNodeEditTags() {
        initWidget(BINDER.createAndBindUi(this));

        addFromAllTagsButton.setOnClick(this::addSelectedTags);
        removeButton.setOnClick(this::removeSelectedNodeTags);
        // Hidden/visible, never disabled
        clearInputButton.setOnClick(this::onClearInputClicked);
        addFromInputButton.setOnClick(this::addTagsFromTextInput);

        nodeTagsList.setModel(nodeTagsModel);
        nodeTagsList.getSelectionModel().addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                removeSelectedNodeTags();
            } else {
                updateButtonStates();
            }
        });
        nodeTagsList.addDomHandler(this::handleNodeTagsKeyDownEvent, KeyDownEvent.getType());

        textBox.addKeyPressHandler(tagsKeyPressHandler);
        textBox.addKeyUpHandler(this::handleTextBoxKeyUpEvent);

        allTagsList.setModel(allTagsModel);
        allTagsList.getSelectionModel().addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                addSelectedTags();
            } else {
                updateButtonStates();
            }
        });
        // Up from the first known tag goes back to the field that filters them
        allTagsList.setMoveUpFromFirst(() -> {
            textBox.setFocus(true);
            textBox.selectAll();
        });
    }

    private void handleNodeTagsKeyDownEvent(final KeyDownEvent event) {
        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_DELETE:
            case KeyCodes.KEY_BACKSPACE:
                // Backspace would otherwise go back a page in some browsers
                event.preventDefault();
                removeSelectedNodeTags();
                break;
        }
    }

    private void handleTextBoxKeyUpEvent(final KeyUpEvent event) {
        //noinspection EnhancedSwitchMigration
        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_ENTER:
                addTagsFromTextInput();
                break;
            case KeyCodes.KEY_DOWN:
                allTagsList.focus();
                break;
            default:
                updateButtonStates();
                updateAllTagsListContents();
        }
    }

    private void removeSelectedNodeTags() {
        final List<String> selected = TagListModel.getSelectedTags(nodeTagsList);
        if (!selected.isEmpty()) {
            selected.forEach(nodeTags::remove);
            updateAllLists();
            // The tags removed are gone from the list, so it keeps the focus for the next, or the
            // field has it once there are none
            if (nodeTags.isEmpty()) {
                textBox.setFocus(true);
            } else {
                nodeTagsList.focus();
            }
        }
    }

    private void onClearInputClicked() {
        clearInput();
        // The button hides itself once the input is empty, which would leave focus nowhere
        textBox.setFocus(true);
    }

    private void addTagsFromTextInput() {
        final String input = textBox.getText();
        if (!NullSafe.isBlankString(input)) {
            // Add the tags from the text box
            nodeTags.addAll(inputToTags(input));
            clearInput();
            updateAllLists();
        }
    }

    private void addSelectedTags() {
        final List<String> selected = TagListModel.getSelectedTags(allTagsList);
        if (!selected.isEmpty()) {
            nodeTags.addAll(selected);
            updateAllLists();
            // As after adding typed tags, ready to type or filter for the next
            textBox.setFocus(true);
        }
    }

    private List<String> inputToTags(final String tagInput) {
        if (NullSafe.isBlankString(tagInput)) {
            return Collections.emptyList();
        } else {
            final SplitResult split = TAG_SPLIT_REGEX.split(cleanTag(tagInput));
            final List<String> tags = new ArrayList<>(split.length());
            for (int i = 0; i < split.length(); i++) {
                tags.add(split.get(i));
            }
            return tags;
        }
    }

    // Trimmed and lower-cased
    private String cleanTag(final String tag) {
        return Objects.requireNonNull(tag, "")
                .trim()
                .toLowerCase();
    }

    /// Focuses the field tags are typed in.
    public void focus() {
        textBox.setFocus(true);
    }

    /// @return The document's tags, or the tags to add to all the documents.
    public Set<String> getNodeTags() {
        return new HashSet<>(nodeTags);
    }

    private void updateAllLists() {
        updateNodeTagsListContents();
        updateAllTagsListContents();
    }

    private void updateNodeTagsListContents() {
        final List<String> tagsList = NullSafe.stream(nodeTags)
                .sorted()
                .collect(Collectors.toList());
        final int documentCount = NullSafe.size(docRefs);
        nodeTagsFormGroup.setLabel(documentCount > 1
                ? "Tags to add to " + documentCount + " Documents"
                : "Document Tags");
        nodeTagsModel.setTags(nodeTagsList, tagsList);
    }

    private void updateAllTagsListContents() {
        final String filter = textBox.getText();

        final Predicate<String> predicate;
        if (NullSafe.isBlankString(filter)) {
            predicate = ALWAYS_TRUE_PREDICATE;
        } else {
            final String lowerCaseFilter = cleanTag(filter);
            predicate = tag -> tag.contains(lowerCaseFilter);
        }

        final List<String> tagsList = allTags.stream()
                .filter(tag -> !nodeTags.contains(tag)) // No point including ones we already have on the node
                .filter(predicate)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        allTagsModel.setTags(allTagsList, tagsList);
        updateButtonStates();
    }

    private void updateButtonStates() {
        updateAddFromInputEnabledState();
        updateClearInputVisibleState();
        addFromAllTagsButton.setEnabled(allTagsList.getSelectionModel().getSelectedCount() > 0);
        removeButton.setEnabled(nodeTagsList.getSelectionModel().getSelectedCount() > 0);
    }

    private void updateClearInputVisibleState() {
        final boolean isInputEmpty = NullSafe.isEmptyString(textBox.getText());
        clearInputButton.setVisible(!isInputEmpty);
        final String text = "All known tags"
                + (isInputEmpty
                ? ""
                : " (filtered)");
        allTagsFormGroup.setLabel(text);
    }

    private void updateAddFromInputEnabledState() {
        addFromInputButton.setEnabled(!NullSafe.isBlankString(textBox.getText()));
    }

    /// Shows the tags of the documents, and all the tags known.
    ///
    /// @param nodeDocRefs The documents.
    /// @param nodeTags    The document's tags, or none for several documents.
    /// @param allTags     All the tags known.
    public void setData(final List<DocRef> nodeDocRefs,
                        final Set<String> nodeTags,
                        final Set<String> allTags) {

        clearInput();
        this.nodeTags.clear();
        this.allTags.clear();
        this.docRefs = nodeDocRefs;
        this.nodeTags.addAll(NullSafe.set(nodeTags));
        this.allTags.addAll(NullSafe.set(allTags));
        // A new set of tags starts with none chosen
        nodeTagsList.getSelectionModel().clear();
        allTagsList.getSelectionModel().clear();
        updateAllLists();
    }

    private void clearInput() {
        textBox.setText("");
        updateButtonStates();
        updateAllLists();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExplorerNodeEditTags> {

    }


    // --------------------------------------------------------------------------------


    // Swallows various key presses to limit the chars the user can enter
    private static class TagsKeyPressHandler implements KeyPressHandler {

        @Override
        public void onKeyPress(final KeyPressEvent event) {

            switch (event.getNativeEvent().getKeyCode()) {
                case KeyCodes.KEY_ALT:
                case KeyCodes.KEY_BACKSPACE:
                case KeyCodes.KEY_CTRL:
                case KeyCodes.KEY_DELETE:
                case KeyCodes.KEY_DOWN:
                case KeyCodes.KEY_END:
                case KeyCodes.KEY_ENTER:
                case KeyCodes.KEY_ESCAPE:
                case KeyCodes.KEY_HOME:
                case KeyCodes.KEY_LEFT:
                case KeyCodes.KEY_PAGEDOWN:
                case KeyCodes.KEY_PAGEUP:
                case KeyCodes.KEY_RIGHT:
                case KeyCodes.KEY_SHIFT:
                case KeyCodes.KEY_SPACE:
                case KeyCodes.KEY_TAB:
                case KeyCodes.KEY_UP:
                    break;
                default:

                    if (event.isAltKeyDown()
                            || (
                            event.isControlKeyDown()
                                    && (event.getCharCode() != 'v' && event.getCharCode() != 'V'))) {
                        break;
                    }

                    if (event.isAltKeyDown()
                            || (
                            event.isControlKeyDown()
                                    && (event.getCharCode() != 'c' && event.getCharCode() != 'C'))) {
                        break;
                    }

                    if (event.isAltKeyDown()
                            || (
                            event.isControlKeyDown()
                                    && (event.getCharCode() != 'x' && event.getCharCode() != 'X'))) {
                        break;
                    }

                    if (event.getSource() instanceof TextBox) {
                        if (!(Character.isDigit(event.getCharCode())
                                || Character.isLetter(event.getCharCode())
                                || event.getCharCode() == '-')) {

                            ((TextBox) event.getSource()).cancelKey();
                        }
                    }
            }
        }
    }
}
