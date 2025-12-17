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

package stroom.receive.content.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.dispatch.client.RestFactory;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.query.client.ExpressionTreePresenter;
import stroom.receive.content.client.presenter.ContentTemplateTabPresenter.ContentTemplateTabView;
import stroom.receive.content.shared.ContentTemplate;
import stroom.receive.content.shared.ContentTemplateResource;
import stroom.receive.content.shared.ContentTemplates;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuBuilder;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.BorderStyle;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ContentTemplateTabPresenter
        extends ContentTabPresenter<ContentTemplateTabView>
        implements HasDirtyHandlers, CloseContentEvent.Handler {

    public static final String TAB_TYPE = "ContentTemplates";
    private static final String TAB_LABEL = "Content Templates";
    private static final ContentTemplateResource CONTENT_TEMPLATE_RESOURCE = GWT.create(
            ContentTemplateResource.class);

    private static final Preset DELETE_TEMPLATE_SVG_PRESET = SvgPresets.DELETE.title("Delete template");
    //    private static final Preset DISABLE_TEMPLATE_SVG_PRESET = SvgPresets.DISABLE.title("Enable/Disable template");
    protected static final Preset ADD_ABOVE_SVG_PRESET = SvgPresets.ADD_ABOVE.title(
            "Add new template above the selected one");
    protected static final Preset ADD_BELOW_SVG_PRESET = SvgPresets.ADD_BELOW.title(
            "Add new template below the selected one");
    protected static final Preset COPY_TEMPLATE_SVG_PRESET = SvgPresets.COPY.title("Copy template");
    protected static final Preset EDIT_TEMPLATE_SVG_PRESET = SvgPresets.EDIT.title("Edit template");
    protected static final Preset MOVE_TEMPLATE_UP_SVG_PRESET = SvgPresets.UP.title("Move template up");
    protected static final Preset MOVE_TEMPLATE_DOWN_SVG_PRESET = SvgPresets.DOWN.title("Move template down");

    private final ContentTemplateListPresenter listPresenter;
    private final ExpressionTreePresenter expressionPresenter;
    private final Provider<ContentTemplateEditPresenter> editPresenterProvider;
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

    @Inject
    public ContentTemplateTabPresenter(final EventBus eventBus,
                                       final ContentTemplateTabView view,
                                       final ContentTemplateListPresenter listPresenter,
                                       final ExpressionTreePresenter expressionPresenter,
                                       final Provider<ContentTemplateEditPresenter> editPresenterProvider,
                                       final RestFactory restFactory) {
        super(eventBus, view);
        this.listPresenter = listPresenter;
        this.expressionPresenter = expressionPresenter;
        this.editPresenterProvider = editPresenterProvider;
        this.restFactory = restFactory;

        getView().setTableView(listPresenter.getView());
//        getView().setExpressionView(expressionPresenter.getView());

        // Stop users from selecting expression items.
        expressionPresenter.setSelectionModel(null);

        saveButton = listPresenter.add(SvgPresets.SAVE.title("Save templates"));
        addAboveButton = listPresenter.add(ADD_ABOVE_SVG_PRESET);
        addBelowButton = listPresenter.add(ADD_BELOW_SVG_PRESET);
        editButton = listPresenter.add(EDIT_TEMPLATE_SVG_PRESET);
        copyButton = listPresenter.add(COPY_TEMPLATE_SVG_PRESET);
        deleteButton = listPresenter.add(DELETE_TEMPLATE_SVG_PRESET);
        moveUpButton = listPresenter.add(MOVE_TEMPLATE_UP_SVG_PRESET);
        moveDownButton = listPresenter.add(MOVE_TEMPLATE_DOWN_SVG_PRESET);

        listPresenter.getView()
                .asWidget()
                .getElement()
                .getStyle()
                .setBorderStyle(BorderStyle.NONE);

        updateButtons();

        initialiseTemplates(restFactory);
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

    @Override
    protected void onBind() {
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

        listPresenter.setActionMenuItemProvider(this::buildActionMenuItems);

        super.onBind();
    }

    private List<Item> buildActionMenuItems(final ContentTemplate contentTemplate) {

        return MenuBuilder.builder()
//                .withSimpleMenuItem(itemBuilder ->
//                        itemBuilder.withText(rule.getRuleNumber() + ". " + rule.getName()))
//                .withSeparator()
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.ADD_ABOVE)
                                .text("Add new template above")
                                .command(() ->
                                        addNewRule(contentTemplate.getTemplateIndex())))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.ADD_BELOW)
                                .text("Add new template below")
                                .command(() ->
                                        addNewRule(contentTemplate.getTemplateNumber())))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.EDIT)
                                .text("Edit template")
                                .command(() ->
                                        editRule(contentTemplate)))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.COPY)
                                .text("Copy template")
                                .command(() ->
                                        copyRule(contentTemplate)))
                .withIconMenuItem(itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.DELETE)
                                .text("Delete template")
                                .command(() ->
                                        deleteRule(contentTemplate)))
//                .withIconMenuItem(itemBuilder ->
//                        itemBuilder
//                                .icon(SvgImage.DISABLE)
//                                .text("Enable/Disable template")
//                                .command(() ->
//                                        setRuleEnabledState(contentTemplate, !contentTemplate.isEnabled())))
                .withIconMenuItemIf(contentTemplate.getTemplateNumber() > 0, itemBuilder ->
                        itemBuilder
                                .icon(SvgImage.UP)
                                .text("Move template Up")
                                .command(() ->
                                        moveRuleUp(contentTemplate)))
                .withIconMenuItemIf(contentTemplate.getTemplateNumber() < getMaxTemplateIndex(),
                        itemBuilder ->
                                itemBuilder
                                        .icon(SvgImage.DOWN)
                                        .text("Move template Down")
                                        .command(() ->
                                                moveRuleDown(contentTemplate)))
                .build();
    }

    private void addEnabledClickHandler() {
        listPresenter.setEnabledStateHandler(this::setRuleEnabledState);
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
        registerHandler(getSelectionModel().addSelectionHandler(event -> {
            final ContentTemplate contentTemplate = getSelected();
            final String description;
            if (contentTemplate != null) {
                description = contentTemplate.getDescription();
                getView().setExpressionView(expressionPresenter.getView());
                expressionPresenter.read(contentTemplate.getExpression());
                if (event.getSelectionType().isDoubleSelect()) {
                    edit(contentTemplate);
                }
            } else {
                description = null;
                getView().setExpressionView(null);
                expressionPresenter.read(null);
            }
            getView().setDescription(description);
            updateButtons();
        }));
    }

    private void addMoveDownButtonHandler() {
        registerHandler(moveDownButton.addClickHandler(event -> {
            if (contentTemplates != null) {
                final ContentTemplate contentTemplate = getSelected();
                moveRuleDown(contentTemplate);
            }
        }));
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
        registerHandler(moveUpButton.addClickHandler(event -> {
            if (contentTemplates != null) {
                final ContentTemplate contentTemplate = getSelected();
                moveRuleUp(contentTemplate);
            }
        }));
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
        registerHandler(deleteButton.addClickHandler(event -> {
            if (contentTemplates != null) {
                final ContentTemplate contentTemplate = getSelected();
                deleteRule(contentTemplate);
            }
        }));
    }

    private void deleteRule(final ContentTemplate rule) {
        if (rule != null) {
            final String nameStr = rule.getName() != null && !rule.getName().isEmpty()
                    ? " \"" + rule.getName() + "\""
                    : "";
            ConfirmEvent.fire(
                    this,
                    "Are you sure you want to delete rule "
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
        registerHandler(copyButton.addClickHandler(event -> {
            if (contentTemplates != null) {
                final ContentTemplate selected = getSelected();
                copyRule(selected);
            }
        }));
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
        registerHandler(editButton.addClickHandler(event -> {
            if (contentTemplates != null) {
                final ContentTemplate selected = getSelected();
                editRule(selected);
            }
        }));
    }

    private void editRule(final ContentTemplate rule) {
        if (rule != null) {
            edit(rule);
        }
    }

    private void addAddAboveButtonHandler() {
        registerHandler(addAboveButton.addClickHandler(event -> {
            if (contentTemplates != null) {
                addNewRule(AddMode.ABOVE);
            }
        }));
    }

    private void addAddBelowButtonHandler() {
        registerHandler(addBelowButton.addClickHandler(event -> {
            if (contentTemplates != null) {
                addNewRule(AddMode.BELOW);
            }
        }));
    }

    private void addSaveButtonHandler() {
        registerHandler(saveButton.addClickHandler(event -> {
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
                    .taskMonitorFactory(this)
                    .exec();
        }));
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

        final ContentTemplateEditPresenter editRulePresenter = editPresenterProvider.get();
        editRulePresenter.read(template);

        final PopupSize popupSize = PopupSize.resizable(1000, 800);
        ShowPopupEvent.builder(editRulePresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Add New Template")
                .onShow(e -> editRulePresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ContentTemplate rule = editRulePresenter.write();
                        if (isTemplateValid(rule)) {
                            contentTemplates.add(idx, rule);
                            update();
                            setDirty(true);
                            setSelected(contentTemplates.get(0));
                            e.hide();
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
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
            AlertEvent.fireError(ContentTemplateTabPresenter.this, msg, null);
        }
        return msg == null;
    }

    private void edit(final ContentTemplate existingRule) {
        final ContentTemplateEditPresenter editRulePresenter = editPresenterProvider.get();
        editRulePresenter.read(existingRule);

        final PopupSize popupSize = PopupSize.resizable(1000, 800);
        ShowPopupEvent.builder(editRulePresenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Edit Template")
                .onShow(e -> listPresenter.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final ContentTemplate rule = editRulePresenter.write();
                        if (isTemplateValid(rule)) {
                            final ContentTemplate contentTemplate = editRulePresenter.write();
                            final int index = contentTemplates.indexOf(existingRule);
                            contentTemplates.set(index, contentTemplate);

                            update();
                            // Only mark the policies as dirty if the rule was actually changed.
                            if (!existingRule.equals(contentTemplate)) {
                                setDirty(true);
                            }

                            setSelected(contentTemplates.get(index));
                            e.hide();
                        } else {
                            e.reset();
                        }
                    } else {
                        e.hide();
                    }
                })
                .fire();
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

            listPresenter.setData(contentTemplates);
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
            DirtyEvent.fire(this, dirty);

            if (lastLabel == null || !lastLabel.equals(getLabel())) {
                lastLabel = getLabel();
                RefreshContentTabEvent.fire(this, this);
            }
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public void onCloseRequest(final CloseContentEvent event) {
        final DirtyMode dirtyMode = event.getDirtyMode();
        if (dirty && DirtyMode.FORCE != dirtyMode) {
            if (DirtyMode.CONFIRM_DIRTY == dirtyMode) {
                ConfirmEvent.fire(this,
                        "There are unsaved changes. Are you sure you want to close this tab?",
                        result -> {
                            event.getCallback().closeTab(result);
                            if (result) {
                                unbind();
                            }
                        });
            } else if (DirtyMode.SKIP_DIRTY == dirtyMode) {
                // Do nothing
            } else {
                throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
            }
        } else {
            event.getCallback().closeTab(true);
            unbind();
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
        return listPresenter.getSelectionModel();
    }

    private void setSelected(final ContentTemplate contentTemplate) {
        listPresenter.getSelectionModel().setSelected(contentTemplate);
    }

    private ContentTemplate getSelected() {
        return listPresenter.getSelectionModel().getSelected();
    }


    // --------------------------------------------------------------------------------


    private enum AddMode {
        ABOVE,
        BELOW,
        ;
    }


    // --------------------------------------------------------------------------------


    public interface ContentTemplateTabView extends View {

        void setTableView(View view);

        void setDescription(final String description);

        void setExpressionView(View view);
    }
}
