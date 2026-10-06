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

package stroom.gwt.workbench.client;

import stroom.gwt.workbench.client.widgets.aceeditor.FormatStories;
import stroom.gwt.workbench.client.widgets.actionmenucell.ActionMenuCellStories;
import stroom.gwt.workbench.client.widgets.buttons.ButtonStories;
import stroom.gwt.workbench.client.widgets.buttons.IconButtonStories;
import stroom.gwt.workbench.client.widgets.buttons.StepControlsStories;
import stroom.gwt.workbench.client.widgets.celllist.CellListStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.DocRefCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.DocumentTypeCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.ExpanderCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.FeedRefCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.SvgCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.UserRefCellStories;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridStories;
import stroom.gwt.workbench.client.widgets.dateandtime.CalendarGridStories;
import stroom.gwt.workbench.client.widgets.dateandtime.CustomDateBoxStories;
import stroom.gwt.workbench.client.widgets.dateandtime.DateTimePickerStories;
import stroom.gwt.workbench.client.widgets.dateandtime.DurationPickerStories;
import stroom.gwt.workbench.client.widgets.dateandtime.TimePickerStories;
import stroom.gwt.workbench.client.widgets.dialogs.AlertDialogStories;
import stroom.gwt.workbench.client.widgets.dialogs.DialogStories;
import stroom.gwt.workbench.client.widgets.dialogs.DialogTitleStories;
import stroom.gwt.workbench.client.widgets.dialogs.ExplorerDropdownPopupStories;
import stroom.gwt.workbench.client.widgets.dialogs.ModalStories;
import stroom.gwt.workbench.client.widgets.dialogs.PromptDialogStories;
import stroom.gwt.workbench.client.widgets.dialogs.StaticDialogStories;
import stroom.gwt.workbench.client.widgets.dialogs.TextBoxPopupDialogStories;
import stroom.gwt.workbench.client.widgets.display.ClassificationLabelStories;
import stroom.gwt.workbench.client.widgets.display.ClassificationWrapperStories;
import stroom.gwt.workbench.client.widgets.display.CopyTextStories;
import stroom.gwt.workbench.client.widgets.display.HtmlPresenterStories;
import stroom.gwt.workbench.client.widgets.display.InfinityLogoStories;
import stroom.gwt.workbench.client.widgets.display.LogoStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.AceEditorStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.MarkdownEditorStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.SourcePresenterStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.SourceViewerStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.TextPresenterStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.XsdBrowserStories;
import stroom.gwt.workbench.client.widgets.entitychecktree.EntityCheckTreeStories;
import stroom.gwt.workbench.client.widgets.feedback.ProgressBarStories;
import stroom.gwt.workbench.client.widgets.feedback.SpinnerStories;
import stroom.gwt.workbench.client.widgets.feedback.TaskCounterStories;
import stroom.gwt.workbench.client.widgets.formatdatetime.FormatDateTimeStories;
import stroom.gwt.workbench.client.widgets.glass.DragGuardContractStories;
import stroom.gwt.workbench.client.widgets.inputs.FileChooserStories;
import stroom.gwt.workbench.client.widgets.inputs.FormGroupStories;
import stroom.gwt.workbench.client.widgets.inputs.LineColInputStories;
import stroom.gwt.workbench.client.widgets.inputs.PasswordInputStories;
import stroom.gwt.workbench.client.widgets.inputs.QuickFilterPanelStories;
import stroom.gwt.workbench.client.widgets.inputs.QuickFilterStories;
import stroom.gwt.workbench.client.widgets.inputs.SingleLineEditorStories;
import stroom.gwt.workbench.client.widgets.inputs.TextInputStories;
import stroom.gwt.workbench.client.widgets.inputs.TickBoxStories;
import stroom.gwt.workbench.client.widgets.inputs.ValueSpinnerStories;
import stroom.gwt.workbench.client.widgets.layout.RoundedPanelStories;
import stroom.gwt.workbench.client.widgets.layout.SplitLayoutPanelStories;
import stroom.gwt.workbench.client.widgets.layout.ThinSplitLayoutPanelStories;
import stroom.gwt.workbench.client.widgets.menu.MenuItemWidgetStories;
import stroom.gwt.workbench.client.widgets.menu.MenuPanelStories;
import stroom.gwt.workbench.client.widgets.overlays.HelpButtonStories;
import stroom.gwt.workbench.client.widgets.overlays.TooltipStories;
import stroom.gwt.workbench.client.widgets.overlays.TooltipWidgetStories;
import stroom.gwt.workbench.client.widgets.popover.InfoPopoverCellStories;
import stroom.gwt.workbench.client.widgets.popover.PopoverStories;
import stroom.gwt.workbench.client.widgets.popuppositioner.PopupContractStories;
import stroom.gwt.workbench.client.widgets.query.ExpressionBuilderStories;
import stroom.gwt.workbench.client.widgets.selectors.DocSelectionBoxStories;
import stroom.gwt.workbench.client.widgets.selectors.DropDownSelectorStories;
import stroom.gwt.workbench.client.widgets.selectors.SelectionBoxStories;
import stroom.gwt.workbench.client.widgets.selectors.UserRefSelectionBoxStories;
import stroom.gwt.workbench.client.widgets.tabs.CurveTabBarStories;
import stroom.gwt.workbench.client.widgets.tabs.LinkTabPanelStories;
import stroom.gwt.workbench.client.widgets.tabs.LinkTabsWidgetStories;
import stroom.gwt.workbench.client.widgets.tree.ExplorerTreeStories;
import stroom.gwt.workbench.client.widgets.tree.HTreeStories;
import stroom.gwt.workbench.client.widgets.tree.TypeFilterStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers the GWT ports of the React Storybook's `Widgets/*` stories (77 components,
/// 308 stories in the React Storybook), in the React sidebar's order.
///
/// ## Adding a story class
///
/// 1. Write the stories class in the sub-package of `stroom.gwt.workbench.client.widgets` for
///    its React group, e.g. `widgets.buttons` for `Widgets/Buttons/*` (see
///    `widgets/buttons/ButtonStories` for an example), giving it a
///    `public static void addTo(StoryRegistry registry)` method that calls
///    `registry.component("<React title>", XxxStories.class)` with exactly the React `title`,
///    and `.story("<React export name>", ...)` for each story, so that the story ids match the
///    React ones (they are checked by `TestReactStoryCoverage`).
/// 2. Call it in [#addTo] on the line after the comment holding its React title, e.g.
///    ```
///    // Widgets/Buttons/Button
///    XxxStories.addTo(registry);
///    ```
///    There is a comment for every React component, so each porter edits a different line, which
///    keeps merge conflicts to a minimum. Leave the comments in place.
///
/// The comments are the titles in `src/test/resources/react-stories.json`. A React component
/// added since then can be added in its sidebar position, or before `return registry;`.
public final class WidgetsStories {

    private WidgetsStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    /// @return The registry.
    public static StoryRegistry addTo(final StoryRegistry registry) {
        // Widgets/Editors & Viewers/AceEditor
        AceEditorStories.addTo(registry);
        // Widgets/Editors & Viewers/MarkdownEditor
        MarkdownEditorStories.addTo(registry);
        // Widgets/Editors & Viewers/SourcePresenter
        SourcePresenterStories.addTo(registry);
        // Widgets/Editors & Viewers/SourceViewer
        SourceViewerStories.addTo(registry);
        // Widgets/Editors & Viewers/TextPresenter
        TextPresenterStories.addTo(registry);
        // Widgets/Editors & Viewers/XsdBrowser
        XsdBrowserStories.addTo(registry);
        // Widgets/AceEditor/format
        FormatStories.addTo(registry);
        // Widgets/ActionMenuCell
        ActionMenuCellStories.addTo(registry);
        // Widgets/Dialogs/AlertDialog
        AlertDialogStories.addTo(registry);
        // Widgets/Dialogs/Dialog
        DialogStories.addTo(registry);
        // Widgets/Dialogs/DialogTitle
        DialogTitleStories.addTo(registry);
        // Widgets/Dialogs/Modal
        ModalStories.addTo(registry);
        // Widgets/Dialogs/ExplorerDropdownPopup
        ExplorerDropdownPopupStories.addTo(registry);
        // Widgets/Dialogs/PromptDialog
        PromptDialogStories.addTo(registry);
        // Widgets/Dialogs/StaticDialog
        StaticDialogStories.addTo(registry);
        // Widgets/Dialogs/TextBoxPopupDialog
        TextBoxPopupDialogStories.addTo(registry);
        // Widgets/Buttons/Button
        ButtonStories.addTo(registry);
        // Widgets/Buttons/IconButton
        IconButtonStories.addTo(registry);
        // Widgets/Buttons/StepControls
        StepControlsStories.addTo(registry);
        // Widgets/Date & Time/CalendarGrid
        CalendarGridStories.addTo(registry);
        // Widgets/Date & Time/CustomDateBox
        CustomDateBoxStories.addTo(registry);
        // Widgets/Date & Time/DateTimePicker
        DateTimePickerStories.addTo(registry);
        // Widgets/Date & Time/DurationPicker
        DurationPickerStories.addTo(registry);
        // Widgets/Date & Time/TimePicker
        TimePickerStories.addTo(registry);
        // Widgets/CellList
        CellListStories.addTo(registry);
        // Widgets/Display/ClassificationLabel
        ClassificationLabelStories.addTo(registry);
        // Widgets/Display/ClassificationWrapper
        ClassificationWrapperStories.addTo(registry);
        // Widgets/Display/CopyText
        CopyTextStories.addTo(registry);
        // Widgets/Display/HtmlPresenter
        HtmlPresenterStories.addTo(registry);
        // Widgets/Display/InfinityLogo
        InfinityLogoStories.addTo(registry);
        // Widgets/Display/Logo
        LogoStories.addTo(registry);
        // Widgets/formatDateTime
        FormatDateTimeStories.addTo(registry);
        // Widgets/Tabs/CurveTabBar
        CurveTabBarStories.addTo(registry);
        // Widgets/Tabs/LinkTabPanel
        LinkTabPanelStories.addTo(registry);
        // Widgets/Tabs/LinkTabsWidget
        LinkTabsWidgetStories.addTo(registry);
        // Widgets/Data Grid/DataGrid
        DataGridStories.addTo(registry);
        // Widgets/Cell Renderers/DocRefCell
        DocRefCellStories.addTo(registry);
        // Widgets/Cell Renderers/DocumentTypeCell
        DocumentTypeCellStories.addTo(registry);
        // Widgets/Cell Renderers/ExpanderCell
        ExpanderCellStories.addTo(registry);
        // Widgets/Cell Renderers/FeedRefCell
        FeedRefCellStories.addTo(registry);
        // Widgets/Cell Renderers/SvgCell
        SvgCellStories.addTo(registry);
        // Widgets/Cell Renderers/UserRefCell
        UserRefCellStories.addTo(registry);
        // Widgets/Selectors/DocSelectionBox
        DocSelectionBoxStories.addTo(registry);
        // Widgets/Selectors/DropDownSelector
        DropDownSelectorStories.addTo(registry);
        // Widgets/Selectors/SelectionBox
        SelectionBoxStories.addTo(registry);
        // Widgets/Selectors/UserRefSelectionBox
        UserRefSelectionBoxStories.addTo(registry);
        // Widgets/Tree/EntityCheckList
        // Widgets/Tree/ExplorerTree
        ExplorerTreeStories.addTo(registry);
        // Widgets/Tree/TypeFilter
        TypeFilterStories.addTo(registry);
        // Widgets/Tree/HTree
        HTreeStories.addTo(registry);
        // Widgets/EntityCheckTree
        EntityCheckTreeStories.addTo(registry);
        // Widgets/Query/ExpressionBuilder
        ExpressionBuilderStories.addTo(registry);
        // Widgets/Inputs/FileChooser
        FileChooserStories.addTo(registry);
        // Widgets/Inputs/FormGroup
        FormGroupStories.addTo(registry);
        // Widgets/Inputs/LineColInput
        LineColInputStories.addTo(registry);
        // Widgets/Inputs/PasswordInput
        PasswordInputStories.addTo(registry);
        // Widgets/Inputs/QuickFilter
        QuickFilterStories.addTo(registry);
        // Widgets/Inputs/QuickFilterPanel
        QuickFilterPanelStories.addTo(registry);
        // Widgets/Inputs/SingleLineEditor
        SingleLineEditorStories.addTo(registry);
        // Widgets/Inputs/TextInput
        TextInputStories.addTo(registry);
        // Widgets/Inputs/TickBox
        TickBoxStories.addTo(registry);
        // Widgets/Inputs/ValueSpinner
        ValueSpinnerStories.addTo(registry);
        // Widgets/Glass/Drag guard contract
        DragGuardContractStories.addTo(registry);
        // Widgets/Overlays/HelpButton
        HelpButtonStories.addTo(registry);
        // Widgets/Overlays/Tooltip
        TooltipStories.addTo(registry);
        // Widgets/Overlays/TooltipWidget
        TooltipWidgetStories.addTo(registry);
        // Widgets/Menu/MenuPanel
        MenuPanelStories.addTo(registry);
        // Widgets/Menu/MenuItemWidget
        MenuItemWidgetStories.addTo(registry);
        // Widgets/Popover/InfoPopoverCell
        InfoPopoverCellStories.addTo(registry);
        // Widgets/Popover/Popover
        PopoverStories.addTo(registry);
        // Widgets/PopupPositioner/Popup contract
        PopupContractStories.addTo(registry);
        // Widgets/Feedback/ProgressBar
        ProgressBarStories.addTo(registry);
        // Widgets/Feedback/Spinner
        SpinnerStories.addTo(registry);
        // Widgets/Feedback/TaskCounter
        TaskCounterStories.addTo(registry);
        // Widgets/Layout/RoundedPanel
        RoundedPanelStories.addTo(registry);
        // Widgets/Layout/SplitLayoutPanel
        SplitLayoutPanelStories.addTo(registry);
        // Widgets/Layout/ThinSplitLayoutPanel
        ThinSplitLayoutPanelStories.addTo(registry);
        return registry;
    }
}
