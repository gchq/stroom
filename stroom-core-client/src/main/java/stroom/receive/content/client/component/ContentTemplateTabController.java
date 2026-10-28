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

package stroom.receive.content.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.CellIcon;
import stroom.content.client.component.ContentTabController;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.client.component.ExpressionTreeController;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplateResource;
import stroom.receive.content.shared.ContentTemplates;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.MenuBuilder;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// Shows the content templates, and adds, edits, moves and deletes them.
public class ContentTemplateTabController extends ContentTabController
        implements CloseContentEvent.Handler {

    public static final String TAB_TYPE = "ContentTemplates";
    private static final CellIcon DELETE_TEMPLATE_ICON = CellIcon.of(SvgImage.DELETE, "Delete template", false);
    protected static final CellIcon COPY_TEMPLATE_ICON = CellIcon.of(SvgImage.COPY, "Copy template", false);
    protected static final CellIcon MOVE_TEMPLATE_UP_ICON = CellIcon.of(SvgImage.UP, "Move template up", false);

    private static final String TAB_LABEL = "Content Templates";
    private static final ContentTemplateResource CONTENT_TEMPLATE_RESOURCE = GWT.create(
            ContentTemplateResource.class);

    //    private static final Preset DISABLE_TEMPLATE_ICON = SvgPresets.DISABLE.title("Enable/Disable template");
    protected static final CellIcon ADD_ABOVE_ICON = CellIcon.of(SvgImage.ADD_ABOVE,
            "Add new template above the selected one");
    protected static final CellIcon ADD_BELOW_ICON = CellIcon.of(SvgImage.ADD_BELOW,
            "Add new template below the selected one");
    protected static final CellIcon EDIT_TEMPLATE_ICON = CellIcon.of(SvgImage.EDIT, "Edit template", false);
    protected static final CellIcon MOVE_TEMPLATE_DOWN_ICON = CellIcon.of(SvgImage.DOWN, "Move template down", false);

    private final ContentTemplateTab view;
    private final ContentTemplateListController contentTemplateList;
    private final ExpressionTreeController expressionTree;
    private final Provider<ContentTemplateEditController> templateEditorProvider;
    private final RestFactory restFactory;

    // Immutable list of templates
    private ContentTemplates contentTemplatesWrapper;
    // The mutable working list of templates
    private List<ContentTemplate> contentTemplates;

    private final ButtonView saveButton;
    private final ButtonView addAboveButton;
    private final ButtonView addBelowButton;
    private final ButtonView editButton;
    private final ButtonView copyButton;
    private final ButtonView deleteButton;
    private final ButtonView moveUpButton;
    private final ButtonView moveDownButton;

    private boolean dirty;
    private String lastLabel;
//    private DataRetentionPresenter dataRetentionPresenter;

    /// Made by GIN, with its screen and its parts.
    @Inject
    public ContentTemplateTabController(final EventBus eventBus,
                                        final ContentTemplateTab view,
                                        final ContentTemplateListController contentTemplateList,
                                        final ExpressionTreeController expressionTree,
                                        final Provider<ContentTemplateEditController> templateEditorProvider,
                                        final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(contentTemplateList);
        own(expressionTree);
        this.contentTemplateList = contentTemplateList;
        this.expressionTree = expressionTree;
        this.templateEditorProvider = templateEditorProvider;
        this.restFactory = restFactory;

        view.setTableView(contentTemplateList);
//        view.setExpressionView(expressionTree);

        // Stop users from selecting expression items.
        expressionTree.setSelectionModel(null);

        contentTemplateList.getToolbar().setName("Content template actions");
        saveButton = contentTemplateList.getToolbar().button(SvgImage.SAVE, "Save Templates", this::onSave);
        saveButton.setEnabled(false);
        addAboveButton = contentTemplateList.getToolbar().button(
                SvgImage.ADD_ABOVE,
                "Add Template Above",
                this::onAddAbove);
        addBelowButton = contentTemplateList.getToolbar().button(
                SvgImage.ADD_BELOW,
                "Add Template Below",
                this::onAddBelow);
        editButton = contentTemplateList.getToolbar().button(SvgImage.EDIT, "Edit Selected Template", this::onEdit);
        editButton.setEnabled(false);
        copyButton = contentTemplateList.getToolbar().button(SvgImage.COPY, "Duplicate Selected Template",
                this::onCopy);
        copyButton.setEnabled(false);
        deleteButton = contentTemplateList.getToolbar().button(SvgImage.DELETE, "Delete Selected Template",
                this::onDelete);
        deleteButton.setEnabled(false);
        moveUpButton = contentTemplateList.getToolbar().button(SvgImage.UP, "Move Template Up", this::onMoveUp);
        moveUpButton.setEnabled(false);
        moveDownButton = contentTemplateList.getToolbar().button(SvgImage.DOWN, "Move Template Down", this::onMoveDown);
        moveDownButton.setEnabled(false);

        updateButtons();

        initialiseTemplates(restFactory);

        addSaveButtonHandler();
        addAddAboveButtonHandler();
        addAddBelowButtonHandler();
        addEditButtonHandler();
        addCopyButtonHandler();
        addDeleteButtonHandler();
        addMoveUpButtonHandler();
        addMoveDownButtonHandler();
        addListSelectionHandler();
        addEnabledClickHandler();

        contentTemplateList.setActionMenuItemProvider(this::buildActionMenuItems);
    }

    private void initialiseTemplates(final RestFactory restFactory) {
        restFactory
                .create(CONTENT_TEMPLATE_RESOURCE)
                .method(ContentTemplateResource::fetch)
                .onSuccess(result -> {
                    contentTemplatesWrapper = result;
                    contentTemplates = new ArrayList<>(result.getContentTemplates());
                    update();
                })
                .owner(this)
                .taskMonitorFactory(this)
                .exec();
    }

    private List<ContentTemplate> getTemplates() {
        return contentTemplates;
    }

    private int getTemplateCount() {
        return contentTemplates.size();
    }

    private int getMaxTemplateIndex() {
        return contentTemplates.size() - 1;
    }

    private void onSave() {
        // Get the user's rules without our default one
        restFactory
                .create(CONTENT_TEMPLATE_RESOURCE)
                .method(res ->
                        res.update(contentTemplatesWrapper.copy()
                                .contentTemplates(getTemplates())
                                .build()))
                .onSuccess(result -> {
                    contentTemplatesWrapper = result;
                    contentTemplates = new ArrayList<>(result.getContentTemplates());
                    getSelectionModel().clear();
                    update();
                    setDirty(false);
                })
                .owner(this)
                .taskMonitorFactory(this)
                .exec();
    }

    private void onAddAbove() {
        if (contentTemplates != null) {
            addNewRule(AddMode.ABOVE);
        }
    }

    private void onAddBelow() {
        if (contentTemplates != null) {
            addNewRule(AddMode.BELOW);
        }
    }

    private void onEdit() {
        if (contentTemplates != null) {
            final ContentTemplate selected = getSelected();
            editRule(selected);
        }
    }

    private void onCopy() {
        if (contentTemplates != null) {
            final ContentTemplate selected = getSelected();
            copyRule(selected);
        }
    }

    private void onDelete() {
        if (contentTemplates != null) {
            final ContentTemplate contentTemplate = getSelected();
            deleteRule(contentTemplate);
        }
    }

    private void onMoveUp() {
        if (contentTemplates != null) {
            final ContentTemplate contentTemplate = getSelected();
            moveRuleUp(contentTemplate);
        }
    }

    private void onMoveDown() {
        if (contentTemplates != null) {
            final ContentTemplate contentTemplate = getSelected();
            moveRuleDown(contentTemplate);
        }
    }

    private List<Item> buildActionMenuItems(final ContentTemplate contentTemplate) {

        return MenuBuilder.builder()
//                .withSimpleMenuItem(itemBuilder ->
//                        itemBuilder.withText(rule.getRuleNumber() + ". " + rule.getName()))
//                .withSeparator()
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.ADD_ABOVE)
                                .text("Add Template Above")
                                .command(() ->
                                        addNewRule(contentTemplate.getTemplateIndex())))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.ADD_BELOW)
                                .text("Add Template Below")
                                .command(() ->
                                        addNewRule(contentTemplate.getTemplateNumber())))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.EDIT)
                                .text("Edit Template")
                                .command(() ->
                                        editRule(contentTemplate)))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.COPY)
                                .text("Duplicate Template")
                                .command(() ->
                                        copyRule(contentTemplate)))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.DELETE)
                                .text("Delete Template")
                                .command(() ->
                                        deleteRule(contentTemplate)))
//                .withIconMenuItem(itemBuilder ->
//                        itemBuilder
//                                .icon(SvgImage.DISABLE)
//                                .text("Enable/Disable template")
//                                .command(() ->
//                                        setRuleEnabledState(contentTemplate, !contentTemplate.isEnabled())))
                .withIconMenuItemIf(contentTemplate.getTemplateNumber() > 1, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.UP)
                                .text("Move Template Up")
                                .command(() ->
                                        moveRuleUp(contentTemplate)))
                .withIconMenuItemIf(contentTemplate.getTemplateNumber() < getTemplateCount(),
                        itemBuilder ->
                                itemBuilder
                                        .icon(SvgImage.DOWN)
                                        .text("Move Template Down")
                                        .command(() ->
                                                moveRuleDown(contentTemplate)))
                .build();
    }

    private void addEnabledClickHandler() {
        contentTemplateList.setOnEnabledChange(this::setRuleEnabledState);
    }

    private void setRuleEnabledState(final ContentTemplate currTemplate, final boolean isEnabled) {
        if (contentTemplates != null) {
            if (currTemplate != null) {
                final ContentTemplate newTemplate = currTemplate.withEnabledState(isEnabled);
                final int index = contentTemplates.indexOf(currTemplate);
                contentTemplates.set(index, newTemplate);
//                    contentTemplates.remove(index);
//                    contentTemplates.add(index, newTemplate);
//                    index = contentTemplates.indexOf(newTemplate);
                update();
                setDirty(true);
                setSelected(contentTemplates.get(index));
            }
        }
    }

    private void addListSelectionHandler() {
        own(getSelectionModel().addSelectionHandler(event -> {
            final ContentTemplate contentTemplate = getSelected();
            final String description;
            if (contentTemplate != null) {
                description = contentTemplate.getDescription();
                view.setExpressionView(expressionTree);
                expressionTree.read(contentTemplate.getExpression());
                if (event.getSelectionType().isDoubleSelect()) {
                    edit(contentTemplate);
                }
            } else {
                description = null;
                view.setExpressionView(null);
                expressionTree.read(null);
            }
            view.setDescription(description);
            updateButtons();
        }));
    }

    private void addMoveDownButtonHandler() {
    }

    private void moveRuleDown(final ContentTemplate rule) {
        if (rule != null) {
            int index = contentTemplates.indexOf(rule);
            // Can't move the last one down any lower
            if (index < getMaxTemplateIndex()) {
                index++;
                contentTemplates.remove(rule);
                contentTemplates.add(index, rule);
                update();
                setDirty(true);

                // Re-select the rule.
                setSelected(contentTemplates.get(index));
            }
        }
    }

    private void addMoveUpButtonHandler() {
    }

    private void moveRuleUp(final ContentTemplate rule) {
        if (rule != null) {
            int index = contentTemplates.indexOf(rule);
            // Can't move the first one up any higher
            if (index > 0) {
                index--;

                contentTemplates.remove(rule);
                contentTemplates.add(index, rule);

                update();
                setDirty(true);

                // Re-select the rule.
                setSelected(contentTemplates.get(index));
            }
        }
    }

    private void addDeleteButtonHandler() {
    }

    private void deleteRule(final ContentTemplate rule) {
        if (rule != null) {
            final String nameStr = rule.getName() != null && !rule.getName().isEmpty()
                    ? " \"" + rule.getName() + "\""
                    : "";
            ConfirmEvent.fire(
                    this,
                    "Are you sure you want to delete template "
                    + rule.getTemplateNumber()
                    + nameStr + "?",
                    ok -> {
                        if (ok) {
                            contentTemplates.remove(rule);

                            update();
                            setDirty(true);

                            // Select the next rule.
                            int index = contentTemplates.indexOf(rule);
                            if (index > 0) {
                                index--;
                            }
                            if (index < getTemplateCount()) {
                                setSelected(contentTemplates.get(index));
                            } else {
                                getSelectionModel().clear();
                            }
                        }
                    });
        }
    }

    private void addCopyButtonHandler() {
    }

    private boolean nameExists(final String name) {
        return contentTemplates.stream()
                .map(ContentTemplate::getName)
                .anyMatch(templateName -> Objects.equals(templateName, name));
    }

    private List<ContentTemplate> findByName(final String name) {
        return contentTemplates.stream()
                .filter(template -> Objects.equals(template.getName(), name))
                .collect(Collectors.toList());
    }

    private void copyRule(final ContentTemplate sourceTemplate) {
        if (sourceTemplate != null) {

            // Make sure the copy has a unique name
            final int sourceIdx = contentTemplates.indexOf(sourceTemplate);
            final int newIdx = sourceIdx + 1;
            int copyNo = 0;
            String newName;
            do {
                copyNo++;
                final String copySuffix = copyNo == 1
                        ? ""
                        : String.valueOf(copyNo);
                newName = sourceTemplate.getName() + " (copy" + copySuffix + ")";
            } while (nameExists(newName));

            final ContentTemplate newTemplate = sourceTemplate.copy()
                    .withTemplateNumber(newIdx + 1) // Make one based
                    .withName(newName)
                    .build();
            contentTemplates.add(newIdx, newTemplate);

            update();
            setDirty(true);
//            getSelectionModel()
//                    .setSelected(contentTemplates.get(sourceIdx));
            setSelected(newTemplate);
        }
    }

    private void addEditButtonHandler() {
    }

    private void editRule(final ContentTemplate rule) {
        if (rule != null) {
            edit(rule);
        }
    }

    private void addAddAboveButtonHandler() {
    }

    private void addAddBelowButtonHandler() {
    }

    private void addSaveButtonHandler() {
    }

    private void addNewRule(final AddMode addMode) {
        Objects.requireNonNull(addMode);
        final ContentTemplate selected = getSelectionModel()
                .getSelected();
        int idx;
        if (selected != null) {
            // adding above the selected
            idx = contentTemplates.indexOf(selected);
            if (AddMode.BELOW == addMode) {
                idx++;
            }
        } else {
            // Nowt selected so add at the very bottom or top
            idx = AddMode.BELOW == addMode
                    ? contentTemplates.size()
                    : 0;
        }
        addNewRule(idx);
    }

    private void addNewRule(final int idx) {
        final ContentTemplate template = ContentTemplate.builder()
                .withTemplateNumber(idx + 1) // idx is zero based, tempNo is 1 based
                .build();

        // Made for one use, and disposed once it is closed
        templateEditorProvider.get().edit(this, "Add New Template", template, rule -> {
            if (!isTemplateValid(rule)) {
                return false;
            }
            contentTemplates.add(idx, rule);
            update();
            setDirty(true);
            setSelected(contentTemplates.get(0));
            return true;
        });
    }

    private boolean isTemplateValid(final ContentTemplate contentTemplate) {
        String msg = null;

        if (NullSafe.isBlankString(contentTemplate.getName())) {
            msg = "The template must have a name.";
        } else if (contentTemplate.getTemplateType() == null) {
            msg = "The template must have a type.";
        } else if (contentTemplate.getPipeline() == null) {
            msg = "The template must have a pipeline.";
        } else {
            final List<ContentTemplate> templates = findByName(contentTemplate.getName());
            if (templates.size() > 1) {
                msg = "Name '" + contentTemplate.getName() + "' is already in use.";
            } else if (templates.size() == 1
                       && templates.get(0).getTemplateNumber() != contentTemplate.getTemplateNumber()) {
                msg = "Name '" + contentTemplate.getName() + "' is already in use.";
            }
        }

        if (msg == null) {
            final ExpressionOperator expression = contentTemplate.getExpression();
            if (!ExpressionUtil.hasTerms(expression)) {
                msg = "The expression must have at least one term.";
            }
        }

        if (msg != null) {
            AlertEvent.fireError(ContentTemplateTabController.this, msg, null);
        }
        return msg == null;
    }

    private void edit(final ContentTemplate existingRule) {
        // Made for one use, and disposed once it is closed
        templateEditorProvider.get().edit(this, "Edit Template", existingRule, contentTemplate -> {
            if (!isTemplateValid(contentTemplate)) {
                return false;
            }
            final int index = contentTemplates.indexOf(existingRule);
            if (index < 0) {
                // Deleted behind the dialog, which isn't modal
                AlertEvent.fireWarn(this, "The template was deleted while it was being "
                                          + "edited, so the changes can't be kept.", null);
            } else {
                contentTemplates.set(index, contentTemplate);

                update();
                // Only mark the policies as dirty if the rule was actually changed.
                if (!existingRule.equals(contentTemplate)) {
                    setDirty(true);
                }

                setSelected(contentTemplates.get(index));
            }
            return true;
        });
    }

    private void update() {
        if (contentTemplates != null) {
            // Set rule numbers on all the rules for display purposes.
//            for (int i = 0; i < contentTemplates.size(); i++) {
//                final ContentTemplate rule = contentTemplates.get(i);
//                final DataRetentionRule newRule = new DataRetentionRule(
//                        i + 1,
//                        rule.getCreationTime(),
//                        rule.getName(),
//                        rule.isEnabled(),
//                        rule.getExpression(),
//                        rule.getAge(),
//                        rule.getTimeUnit(),
//                        rule.isForever());
//                contentTemplates.set(i, newRule);
//            }
            // Ensure all the template numbers are correct after any moves/deletes/copies etc.
            final List<ContentTemplate> tempList = ContentTemplates.resetTemplateNumbers(contentTemplates);
            contentTemplates.clear();
            contentTemplates.addAll(tempList);

            contentTemplateList.setData(contentTemplates);
            // Update the policy so the impact tab can see the unsaved changes
//            contentTemplates.setRules(getTemplates());
        }
        updateButtons();
    }

    private void updateButtons() {
        final boolean loadedPolicy = contentTemplates != null;
        final ContentTemplate selection = getSelected();
        final boolean selected = loadedPolicy && selection != null;
        int index = -1;
        if (selected) {
            index = contentTemplates.indexOf(selection);
        }
//        GWT.log("index: " + index);

        saveButton.setEnabled(loadedPolicy && dirty);
        addAboveButton.setEnabled(loadedPolicy);
        addBelowButton.setEnabled(loadedPolicy);
        editButton.setEnabled(selected);
        copyButton.setEnabled(selected);
        deleteButton.setEnabled(selected);
        moveUpButton.setEnabled(selected
                                && index > 0
                                && index <= getMaxTemplateIndex());
        moveDownButton.setEnabled(selected
                                  && index >= 0
                                  && index < getMaxTemplateIndex());
    }

    boolean isDirty() {
        return dirty;
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            saveButton.setEnabled(dirty);

            if (lastLabel == null || !lastLabel.equals(getLabel())) {
                lastLabel = getLabel();
                RefreshContentTabEvent.fire(this, this);
            }
        }
    }

    @Override
    public void onCloseRequest(final CloseContentEvent event) {
        final DirtyMode dirtyMode = event.getDirtyMode();
        if (dirty && DirtyMode.FORCE != dirtyMode) {
            if (DirtyMode.CONFIRM_DIRTY == dirtyMode) {
                ConfirmEvent.fire(this,
                        "There are unsaved changes. Are you sure you want to close this tab?",
                        result -> {
                            // Closing the tab disposes this
                            event.getCallback().closeTab(result);
                        });
            } else if (DirtyMode.SKIP_DIRTY == dirtyMode) {
                // Do nothing
            } else {
                throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
            }
        } else {
            event.getCallback().closeTab(true);
        }
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.STAMP;
    }

    @Override
    public String getLabel() {
        if (isDirty()) {
            return "* " + TAB_LABEL;
        }
        return TAB_LABEL;
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }

    private MultiSelectionModel<ContentTemplate> getSelectionModel() {
        return contentTemplateList.getSelectionModel();
    }

    private void setSelected(final ContentTemplate contentTemplate) {
        contentTemplateList.getSelectionModel().setSelected(contentTemplate);
    }

    private ContentTemplate getSelected() {
        return contentTemplateList.getSelectionModel().getSelected();
    }

    // --------------------------------------------------------------------------------

    private enum AddMode {
        ABOVE,
        BELOW,
        ;
    }
}
