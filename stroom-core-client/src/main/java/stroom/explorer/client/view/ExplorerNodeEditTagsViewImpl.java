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

package stroom.explorer.client.view;

import stroom.docref.DocRef;
import stroom.explorer.client.presenter.ExplorerNodeEditTagsPresenter.ExplorerNodeEditTagsView;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.form.client.FormGroup;
import stroom.widget.popup.client.view.HideRequestUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.DoubleClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.event.dom.client.KeyPressHandler;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.regexp.shared.SplitResult;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ExplorerNodeEditTagsViewImpl
        extends ViewWithUiHandlers<HideRequestUiHandlers>
        implements ExplorerNodeEditTagsView {

    private static final RegExp TAG_SPLIT_REGEX = RegExp.compile(" +");
    private static final Predicate<String> ALWAYS_TRUE_PREDICATE = (str) -> true;

    private final Widget widget;
    private final ButtonView removeButton;
    private final ButtonView addFromInputButton;
    private final ButtonView clearInputButton;
    private final ButtonView addFromAllTagsButton;
    private final KeyPressHandler tagsKeyPressHandler = new TagsKeyPressHandler();

    //    private SelectionBoxModel<String> model;
    private Set<String> nodeTags = new HashSet<>();
    private Set<String> allTags = new HashSet<>();
    private String selectedValue = null;

    @UiField
    TextBox textBox;
    @UiField
    ButtonPanel clearButtonPanel;
    @UiField
    ButtonPanel inputButtonPanel;
    @UiField
    FormGroup allTagsFormGroup;
    @UiField
    ListBox allTagsListBox;
    @UiField
    ButtonPanel nodeTagsButtonPanel;
    @UiField
    FormGroup nodeTagsFormGroup;
    @UiField
    ListBox nodeTagsListBox;
    private List<DocRef> docRefs;

    @Inject
    public ExplorerNodeEditTagsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        widget.addAttachHandler(event -> focus());

        addFromAllTagsButton = nodeTagsButtonPanel.addButton(
                SvgPresets.ARROW_RIGHT.title("Add selected tags from 'All Known Tags'"));
        addFromAllTagsButton.setEnabled(false);
        addFromAllTagsButton.addClickHandler(this::onAddFromAllTagsClicked);

        removeButton = nodeTagsButtonPanel.addButton(
                SvgPresets.ARROW_LEFT.title("Remove selected tags"));
        removeButton.setEnabled(false);
        removeButton.addClickHandler(this::onDeleteClicked);

        clearInputButton = clearButtonPanel.addButton(
                SvgPresets.CLEAR.title("Clear entered tag(s)"));
        clearInputButton.setEnabled(true); // hidden/visible, never disabled
        clearInputButton.addClickHandler(this::onClearInputClicked);
        clearInputButton.asWidget().addStyleName("clear");

        addFromInputButton = inputButtonPanel.addButton(
                SvgPresets.ADD.title("Add entered tag(s)"));
        addFromInputButton.setEnabled(false);
        addFromInputButton.addClickHandler(this::onAddFromInputClicked);

        nodeTagsListBox.setMultipleSelect(true);
        nodeTagsListBox.addKeyDownHandler(this::handleNodeTagsKeyDownEvent);
        nodeTagsListBox.addClickHandler(this::handleNodeTagsClickEvent);
        nodeTagsListBox.addDoubleClickHandler(this::handleNodeTagsDoubleClickEvent);
        allTagsListBox.setTitle("All tags currently in use across all documents");

        textBox.addKeyPressHandler(tagsKeyPressHandler);
        textBox.addKeyUpHandler(this::handleTextBoxKeyUpEvent);
        textBox.setTitle("Enter tags manually or filter 'All Known Tags'");

        allTagsListBox.setMultipleSelect(true);
        allTagsListBox.addKeyDownHandler(this::handleAllTagsKeyDownEvent);
        allTagsListBox.addClickHandler(this::handleAllTagsClickEvent);
        allTagsListBox.addDoubleClickHandler(this::handleAllTagsDoubleClickEvent);
        allTagsListBox.setTitle("All tags currently in use across all documents");

    }

    private void handleNodeTagsKeyDownEvent(final KeyDownEvent event) {
        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_DELETE:
            case KeyCodes.KEY_BACKSPACE:
            case KeyCodes.KEY_H:
            case KeyCodes.KEY_LEFT:
                removedSelectedNodeTags();
                break;
            case KeyCodes.KEY_J:
                moveSelectionDown(nodeTagsListBox);
                break;
            case KeyCodes.KEY_K:
                moveSelectionUp(nodeTagsListBox);
                break;
//            case KeyCodes.KEY_TAB:
//                textBox.setText(allTagsListBox.getSelectedValue());
//                break;
        }
    }

    private void moveSelectionDown(final ListBox listBox) {
        final int selectedIndex = listBox.getSelectedIndex();
        final int lastIdx = listBox.getItemCount() - 1;
        final int newIdx;
        if (selectedIndex == -1) {
            newIdx = 0;
        } else if (selectedIndex == lastIdx) {
            newIdx = selectedIndex;
        } else {
            newIdx = selectedIndex + 1;
        }
        listBox.setSelectedIndex(newIdx);
        selectedValue = listBox.getSelectedValue();
    }

    private void moveSelectionUp(final ListBox listBox) {
        final int selectedIndex = listBox.getSelectedIndex();
        final int newIdx;
        if (selectedIndex == -1) {
            newIdx = 0;
        } else if (selectedIndex == 0) {
            newIdx = selectedIndex;
        } else {
            newIdx = selectedIndex - 1;
        }
        listBox.setSelectedIndex(newIdx);
        selectedValue = listBox.getSelectedValue();
    }

    private void handleNodeTagsClickEvent(final ClickEvent event) {
        updateButtonStates();
    }

    private void handleNodeTagsDoubleClickEvent(final DoubleClickEvent event) {
        NullSafe.consume(nodeTagsListBox.getSelectedValue(), selectedTag -> {
            nodeTags.remove(selectedTag);
            updateAllListBoxes();
        });
    }

    private void handleAllTagsClickEvent(final ClickEvent event) {
        updateButtonStates();
        selectedValue = allTagsListBox.getSelectedValue();
    }

    private void handleAllTagsDoubleClickEvent(final DoubleClickEvent event) {
        selectedValue = allTagsListBox.getSelectedValue();
        addSelectedTags();
//            clearInput();
        updateAllTagsListBoxContents();
    }

    private void handleAllTagsKeyDownEvent(final KeyDownEvent event) {
        //            GWT.log("allTagsListBox eventTarget: " + event.getNativeEvent().getEventTarget());
        //noinspection EnhancedSwitchMigration
        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_DOWN:
//                textBox.setText(allTagsListBox.getSelectedValue());
                selectedValue = allTagsListBox.getSelectedValue();
                break;
            case KeyCodes.KEY_J:
                moveSelectionDown(allTagsListBox);
                break;
            case KeyCodes.KEY_UP:
//                textBox.setText(allTagsListBox.getSelectedValue());
                // Allow the user to move up from the list to the text field
                if (allTagsListBox.getItemCount() == 0
                        || (allTagsListBox.getSelectedIndex() == 0
                        && Objects.equals(allTagsListBox.getSelectedValue(), selectedValue))) {
                    textBox.setFocus(true);
                    textBox.selectAll();
                }
                selectedValue = allTagsListBox.getSelectedValue();
                break;
            case KeyCodes.KEY_K:
                moveSelectionUp(allTagsListBox);
                break;
            case KeyCodes.KEY_ENTER:
            case KeyCodes.KEY_L:
            case KeyCodes.KEY_RIGHT:
//                    GWT.log("allTagsListBox ENTER clicked");
                addSelectedTags();

                textBox.setFocus(true);
//                if (allTagsListBox.getItemCount() == 0) {
                // None left so go back up
//                    textBox.setFocus(true);
//                }
//                clearInput();
                updateAllTagsListBoxContents();
                break;
//            case KeyCodes.KEY_TAB:
//                textBox.setText(allTagsListBox.getSelectedValue());
//                break;
        }
    }

    private void handleTextBoxKeyUpEvent(final KeyUpEvent event) {
//            GWT.log("textBox eventTarget: " + event.getNativeEvent().getEventTarget());
        //noinspection EnhancedSwitchMigration
        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_ENTER:
//                    GWT.log("textBox ENTER clicked");
                addTagsFromTextInput();
                break;
            case KeyCodes.KEY_DOWN:
                allTagsListBox.setFocus(true);
                selectedValue = allTagsListBox.getSelectedValue();
//                textBox.setText(allTagsListBox.getSelectedValue());
                break;
            default:
//                final String text = cleanTag(textBox.getText());
////                    if (!Objects.equals(textBox.getText(), text)) {
////                        // replace any uppercase with lowercase
////                        textBox.setText(text);
////                    }
                updateButtonStates();
                updateAllTagsListBoxContents();
        }
    }

    private void onDeleteClicked(final ClickEvent event) {
        removedSelectedNodeTags();
//        if (nodeTagsListBox.getItemCount() > 0
//                && nodeTagsListBox.getSelectedValue() != null) {
//            if (nodeTagsListBox.isMultipleSelect()) {
//                for (int i = 0; i < nodeTagsListBox.getItemCount(); i++) {
//                    if (nodeTagsListBox.isItemSelected(i)) {
//                        nodeTags.remove(nodeTagsListBox.getValue(i));
//                    }
//                }
//            } else {
//                nodeTags.remove(nodeTagsListBox.getSelectedValue());
//            }
//            updateAllListBoxes();
//        }
    }

    private void removedSelectedNodeTags() {
        if (nodeTagsListBox.getItemCount() > 0
                && nodeTagsListBox.getSelectedValue() != null) {
            if (nodeTagsListBox.isMultipleSelect()) {
                for (int i = 0; i < nodeTagsListBox.getItemCount(); i++) {
                    if (nodeTagsListBox.isItemSelected(i)) {
                        nodeTags.remove(nodeTagsListBox.getValue(i));
                    }
                }
            } else {
                nodeTags.remove(nodeTagsListBox.getSelectedValue());
            }
            updateAllListBoxes();
        }
    }

    private void onAddFromInputClicked(final ClickEvent event) {
        addTagsFromTextInput();
    }

    private void onClearInputClicked(final ClickEvent event) {
        clearInput();
    }

    private void addTagsFromTextInput() {
        final String input = textBox.getText();
        if (!NullSafe.isBlankString(input)) {
            // Add the tags from the text box
            nodeTags.addAll(inputToTags(input));
            clearInput();
            updateAllListBoxes();
        }
    }

    private void onAddFromAllTagsClicked(final ClickEvent event) {
        addSelectedTags();
    }

    private void addSelectedTags() {
        if (allTagsListBox.getItemCount() > 0
                && allTagsListBox.getSelectedIndex() != -1) {

            if (allTagsListBox.isMultipleSelect()) {
                for (int i = 0; i < allTagsListBox.getItemCount(); i++) {
                    if (allTagsListBox.isItemSelected(i)) {
                        nodeTags.add(allTagsListBox.getValue(i));
                    }
                }
            } else {
                nodeTags.add(allTagsListBox.getSelectedValue());
            }
            updateAllListBoxes();
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

    /**
     * @return Trimmed and lower-cased tag.
     */
    private String cleanTag(final String tag) {
        return Objects.requireNonNull(tag, "")
                .trim()
                .toLowerCase();
    }

    @Override
    public void focus() {
        textBox.setFocus(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public Set<String> getNodeTags() {
        return new HashSet<>(nodeTags);
    }

    private void updateAllListBoxes() {
        updateNodeTagsListBoxContents();
        updateAllTagsListBoxContents();
    }

    private void updateNodeTagsListBoxContents() {
        //noinspection SimplifyStreamApiCallChains
        final List<String> tagsList = NullSafe.stream(nodeTags)
                .sorted()
                .collect(Collectors.toList());
        final int documentCount = NullSafe.size(docRefs);
        nodeTagsFormGroup.setLabel(documentCount > 1
                ? "Tags to add to " + documentCount + " Documents"
                : "Document Tags");

        nodeTagsListBox.clear();
        for (final String string : tagsList) {
            nodeTagsListBox.addItem(string);
        }
        if (nodeTagsListBox.getItemCount() > 0) {
            nodeTagsListBox.setSelectedIndex(0);
            updateButtonStates();
        }
    }

//    private void updateAllTagsListBoxContents() {
//        updateAllTagsListBoxContents(null);
//    }

    private void updateAllTagsListBoxContents() {
        final String filter = textBox.getText();
        allTagsListBox.clear();

        final Predicate<String> predicate;
        if (NullSafe.isBlankString(filter)) {
            predicate = ALWAYS_TRUE_PREDICATE;
        } else {
            final String lowerCaseFilter = cleanTag(filter);
            GWT.log("lowerCaseFilter: '" + lowerCaseFilter + "'");
            predicate = tag -> tag.contains(lowerCaseFilter);
        }

        // Include any newly added node tags in the list of all tags
        allTags.stream()
                .filter(tag -> !nodeTags.contains(tag)) // No point including ones we already have on the node
                .filter(predicate)
                .distinct()
                .sorted()
                .forEach(allTagsListBox::addItem);

        if (allTagsListBox.getItemCount() > 0) {
            if (selectedValue != null) {
                int selectedIdx = -1;
                for (int i = 0; i < allTagsListBox.getItemCount(); i++) {
                    if (Objects.equals(selectedValue, allTagsListBox.getValue(i))) {
                        selectedIdx = i;
                        break;
                    }
                }
                if (selectedIdx != -1) {
                    allTagsListBox.setSelectedIndex(selectedIdx);
                } else {
                    allTagsListBox.setSelectedIndex(0);
                }
            } else {
                allTagsListBox.setSelectedIndex(0);
            }
        }
        updateButtonStates();
//        updateAddFromAllTagsEnabledState();
    }

    private void updateButtonStates() {
        updateAddFromInputEnabledState();
        updateClearInputVisibleState();
        updateAddFromAllTagsEnabledState();
        updateRemoveEnabledState();
    }

    private void updateAddFromAllTagsEnabledState() {
        addFromAllTagsButton.setEnabled(allTagsListBox.getItemCount() > 0
                && allTagsListBox.getSelectedIndex() != -1);
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

    private void updateRemoveEnabledState() {
        removeButton.setEnabled(nodeTagsListBox.getItemCount() > 0
                && nodeTagsListBox.getSelectedIndex() != -1);
    }

    @Override
    public void setData(final List<DocRef> nodeDocRefs,
                        final Set<String> nodeTags,
                        final Set<String> allTags) {

        clearInput();
        this.nodeTags.clear();
        this.allTags.clear();
        this.docRefs = nodeDocRefs;
        this.nodeTags.addAll(NullSafe.set(nodeTags));
        this.allTags.addAll(NullSafe.set(allTags));
        updateNodeTagsListBoxContents();
        updateAllTagsListBoxContents();
    }

    private void clearInput() {
        textBox.setText("");
//        selectedValue = null;
        updateButtonStates();
        updateAllListBoxes();
    }

//    private void setInput(final String text) {
//        textBox.setText(text);
////        selectedValue = null;
//        updateClearInputEnabledState();
//    }


    // --------------------------------------------------------------------------------


    public interface Binder extends UiBinder<Widget, ExplorerNodeEditTagsViewImpl> {

    }

    // --------------------------------------------------------------------------------


    /**
     * Handler to swallow various key presses to limit the chars the user can enter
     */
    private static class TagsKeyPressHandler implements KeyPressHandler {

        @Override
        public void onKeyPress(final KeyPressEvent event) {

//            GWT.log("keypress: " + event.getCharCode());

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
