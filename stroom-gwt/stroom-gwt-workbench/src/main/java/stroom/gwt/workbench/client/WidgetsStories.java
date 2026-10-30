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

import stroom.gwt.workbench.client.widgets.aceeditor.AceEditorFormatStories;
import stroom.gwt.workbench.client.widgets.actionmenucell.ActionMenuCellStories;
import stroom.gwt.workbench.client.widgets.buttons.ButtonGroupStories;
import stroom.gwt.workbench.client.widgets.buttons.ButtonStories;
import stroom.gwt.workbench.client.widgets.buttons.FabButtonStories;
import stroom.gwt.workbench.client.widgets.buttons.RefreshButtonStories;
import stroom.gwt.workbench.client.widgets.buttons.StepControlsStories;
import stroom.gwt.workbench.client.widgets.buttons.ToolbarButtonStateStories;
import stroom.gwt.workbench.client.widgets.buttons.ToolbarButtonStories;
import stroom.gwt.workbench.client.widgets.buttons.ToolbarStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.DocRefCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.DocumentTypeCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.ExpanderCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.FeedRefCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.IconCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.TickBoxCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.UserRefCellStories;
import stroom.gwt.workbench.client.widgets.cellrenderers.ValueSpinnerCellStories;
import stroom.gwt.workbench.client.widgets.datagrid.BehaviourStories;
import stroom.gwt.workbench.client.widgets.datagrid.BenchmarkStories;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridKeyboardStories;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridRowStateStories;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridRowUpdateStories;
import stroom.gwt.workbench.client.widgets.datagrid.DataGridStories;
import stroom.gwt.workbench.client.widgets.dateandtime.CalendarGridStories;
import stroom.gwt.workbench.client.widgets.dateandtime.DateTimePickerStories;
import stroom.gwt.workbench.client.widgets.dateandtime.DurationPickerStories;
import stroom.gwt.workbench.client.widgets.dateandtime.ScheduleBoxStories;
import stroom.gwt.workbench.client.widgets.dateandtime.TimePickerStories;
import stroom.gwt.workbench.client.widgets.dialogs.AlertDialogStories;
import stroom.gwt.workbench.client.widgets.dialogs.DialogStories;
import stroom.gwt.workbench.client.widgets.dialogs.DialogTitleStories;
import stroom.gwt.workbench.client.widgets.dialogs.DocumentChooserStories;
import stroom.gwt.workbench.client.widgets.dialogs.EscapeStories;
import stroom.gwt.workbench.client.widgets.dialogs.ModalStories;
import stroom.gwt.workbench.client.widgets.dialogs.NameDialogStories;
import stroom.gwt.workbench.client.widgets.dialogs.StaticDialogStories;
import stroom.gwt.workbench.client.widgets.display.ClassificationLabelStories;
import stroom.gwt.workbench.client.widgets.display.ClassificationWrapperStories;
import stroom.gwt.workbench.client.widgets.display.CopyTextStories;
import stroom.gwt.workbench.client.widgets.display.HtmlPaneStories;
import stroom.gwt.workbench.client.widgets.display.InfinityLogoStories;
import stroom.gwt.workbench.client.widgets.display.LogoStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.AceEditorFeatureStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.AceEditorStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.AceEditorThemeStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.DataTextPaneStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.ItemNavigatorStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.MarkdownEditorStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.SourceStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.SourceViewerStories;
import stroom.gwt.workbench.client.widgets.editorsandviewers.XsdBrowserStories;
import stroom.gwt.workbench.client.widgets.entitychecktree.DocumentTickTreeStories;
import stroom.gwt.workbench.client.widgets.feedback.BusySpinnerStories;
import stroom.gwt.workbench.client.widgets.feedback.SourceProgressBarStories;
import stroom.gwt.workbench.client.widgets.feedback.TaskCounterStories;
import stroom.gwt.workbench.client.widgets.formatdatetime.FormatDateTimeStories;
import stroom.gwt.workbench.client.widgets.inputs.DynamicSettingsPaneStories;
import stroom.gwt.workbench.client.widgets.inputs.FieldEndStories;
import stroom.gwt.workbench.client.widgets.inputs.FileChooserStories;
import stroom.gwt.workbench.client.widgets.inputs.FormGroupStories;
import stroom.gwt.workbench.client.widgets.inputs.LineColInputStories;
import stroom.gwt.workbench.client.widgets.inputs.PasswordInputStories;
import stroom.gwt.workbench.client.widgets.inputs.QuickFilterPanelStories;
import stroom.gwt.workbench.client.widgets.inputs.QuickFilterStories;
import stroom.gwt.workbench.client.widgets.inputs.RadioGroupStories;
import stroom.gwt.workbench.client.widgets.inputs.SuggestionBoxStories;
import stroom.gwt.workbench.client.widgets.inputs.TextInputStories;
import stroom.gwt.workbench.client.widgets.inputs.TickBoxStories;
import stroom.gwt.workbench.client.widgets.inputs.ValueSpinnerStories;
import stroom.gwt.workbench.client.widgets.inputs.ViewStateStories;
import stroom.gwt.workbench.client.widgets.layout.RoundedPanelStories;
import stroom.gwt.workbench.client.widgets.layout.SplitPaneContentStories;
import stroom.gwt.workbench.client.widgets.layout.SplitPaneResizingStories;
import stroom.gwt.workbench.client.widgets.layout.SplitPaneStateStories;
import stroom.gwt.workbench.client.widgets.layout.SplitPaneStories;
import stroom.gwt.workbench.client.widgets.menu.MenuItemCellStories;
import stroom.gwt.workbench.client.widgets.menu.MenuPanelStories;
import stroom.gwt.workbench.client.widgets.menu.TextContextMenuStories;
import stroom.gwt.workbench.client.widgets.overlays.InfoTooltipStories;
import stroom.gwt.workbench.client.widgets.overlays.TooltipStories;
import stroom.gwt.workbench.client.widgets.popover.InfoColumnStories;
import stroom.gwt.workbench.client.widgets.popover.PopoverStories;
import stroom.gwt.workbench.client.widgets.popuppositioner.PopupContractStories;
import stroom.gwt.workbench.client.widgets.query.ExpressionEditorStories;
import stroom.gwt.workbench.client.widgets.selectors.DocSelectionStories;
import stroom.gwt.workbench.client.widgets.selectors.DropDownBoxStories;
import stroom.gwt.workbench.client.widgets.selectors.SelectionBoxStories;
import stroom.gwt.workbench.client.widgets.selectors.SelectionListStateStories;
import stroom.gwt.workbench.client.widgets.selectors.UserRefSelectionStories;
import stroom.gwt.workbench.client.widgets.tabs.CurveTabBarStories;
import stroom.gwt.workbench.client.widgets.tabs.LinkTabPanelStories;
import stroom.gwt.workbench.client.widgets.tree.ExplorerTreeStories;
import stroom.gwt.workbench.client.widgets.tree.TypeFilterStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers the workbench's `Widgets/*` stories.
///
/// ## Adding a story class
///
/// 1. Write the stories class in the sub-package of `stroom.gwt.workbench.client.widgets` for its
///    group, e.g. `widgets.buttons` for `Widgets/Buttons/*` (see `widgets/buttons/ButtonStories`
///    for an example), giving it a `public static void addTo(StoryRegistry registry)` method that
///    calls `registry.component("<title>", XxxStories.class)` and `.story("<export name>", ...)`
///    for each story. The title and export name make the story's id and URL (see
///    `WRITING-STORIES.md`).
/// 2. Call it in [#addTo] on the line after the comment holding its title, e.g.
///    ```
///    // Widgets/Buttons/Button
///    XxxStories.addTo(registry);
///    ```
///    or, for a new title, add a comment for it next to the related titles. Each title has its
///    own line, so that parallel work rarely touches the same lines. Leave the comments in place.
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
        AceEditorFeatureStories.addTo(registry);
        AceEditorThemeStories.addTo(registry);
        // Widgets/Editors & Viewers/ItemNavigator
        ItemNavigatorStories.addTo(registry);
        // Widgets/Editors & Viewers/MarkdownEditor
        MarkdownEditorStories.addTo(registry);
        // Widgets/Editors & Viewers/SourcePresenter
        SourceStories.addTo(registry);
        // Widgets/Editors & Viewers/SourceViewer
        SourceViewerStories.addTo(registry);
        // Widgets/Editors & Viewers/DataTextPane
        DataTextPaneStories.addTo(registry);
        // Widgets/Editors & Viewers/XsdBrowser
        XsdBrowserStories.addTo(registry);
        // Widgets/AceEditor/format
        AceEditorFormatStories.addTo(registry);
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
        // Widgets/Dialogs/Escape
        EscapeStories.addTo(registry);
        // Widgets/Dialogs/ExplorerDropdownPopup
        DocumentChooserStories.addTo(registry);
        // Widgets/Dialogs/StaticDialog
        StaticDialogStories.addTo(registry);
        // Widgets/Dialogs/NameDialog
        NameDialogStories.addTo(registry);
        // Widgets/Buttons/Button
        ButtonStories.addTo(registry);
        // Widgets/Buttons/ButtonGroup
        ButtonGroupStories.addTo(registry);
        // Widgets/Buttons/FabButton
        FabButtonStories.addTo(registry);
        // Widgets/Buttons/IconButton
        ToolbarButtonStories.addTo(registry);
        // Widgets/Buttons/RefreshButton
        RefreshButtonStories.addTo(registry);
        // Widgets/Buttons/StepControls
        StepControlsStories.addTo(registry);
        // Widgets/Buttons/Toolbar
        ToolbarStories.addTo(registry);
        // Widgets/Buttons/ToolbarButtonStates
        ToolbarButtonStateStories.addTo(registry);
        // Widgets/Date & Time/CalendarGrid
        CalendarGridStories.addTo(registry);
        // Widgets/Date & Time/DateTimePicker
        DateTimePickerStories.addTo(registry);
        // Widgets/Date & Time/DurationPicker
        DurationPickerStories.addTo(registry);
        // Widgets/Date & Time/ScheduleBox
        ScheduleBoxStories.addTo(registry);
        // Widgets/Date & Time/TimePicker
        TimePickerStories.addTo(registry);
        // Widgets/Display/ClassificationLabel
        ClassificationLabelStories.addTo(registry);
        // Widgets/Display/ClassificationWrapper
        ClassificationWrapperStories.addTo(registry);
        // Widgets/Display/CopyText
        CopyTextStories.addTo(registry);
        // Widgets/Display/HtmlPane
        HtmlPaneStories.addTo(registry);
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
        // Widgets/Data Grid/DataGrid
        DataGridStories.addTo(registry);
        // Widgets/Data Grid/Benchmark
        BenchmarkStories.addTo(registry);
        BehaviourStories.addTo(registry);
        // Widgets/Data Grid/DataGridRowStates
        DataGridRowStateStories.addTo(registry);
        // Widgets/Data Grid/DataGridKeyboard
        DataGridKeyboardStories.addTo(registry);
        // Widgets/Data Grid/DataGridRowUpdates
        DataGridRowUpdateStories.addTo(registry);
        // Widgets/Cell Renderers/DocRefCell
        DocRefCellStories.addTo(registry);
        // Widgets/Cell Renderers/DocumentTypeCell
        DocumentTypeCellStories.addTo(registry);
        // Widgets/Cell Renderers/ExpanderCell
        ExpanderCellStories.addTo(registry);
        // Widgets/Cell Renderers/FeedRefCell
        FeedRefCellStories.addTo(registry);
        // Widgets/Cell Renderers/IconCell
        IconCellStories.addTo(registry);
        // Widgets/Cell Renderers/TickBoxCell
        TickBoxCellStories.addTo(registry);
        // Widgets/Cell Renderers/UserRefCell
        UserRefCellStories.addTo(registry);
        // Widgets/Cell Renderers/ValueSpinnerCell
        ValueSpinnerCellStories.addTo(registry);
        // Widgets/Selectors/DocSelectionBox
        DocSelectionStories.addTo(registry);
        // Widgets/Selectors/DropDownSelector
        DropDownBoxStories.addTo(registry);
        // Widgets/Selectors/SelectionBox
        SelectionBoxStories.addTo(registry);
        SelectionListStateStories.addTo(registry);
        // Widgets/Selectors/UserRefSelectionBox
        UserRefSelectionStories.addTo(registry);
        // Widgets/Tree/EntityCheckList
        // Widgets/Tree/ExplorerTree
        ExplorerTreeStories.addTo(registry);
        // Widgets/Tree/TypeFilter
        TypeFilterStories.addTo(registry);
        // Widgets/EntityCheckTree
        DocumentTickTreeStories.addTo(registry);
        // Widgets/Query/ExpressionBuilder
        ExpressionEditorStories.addTo(registry);
        // Widgets/Inputs/DynamicSettingsPane
        DynamicSettingsPaneStories.addTo(registry);
        // Widgets/Inputs/FieldEnds
        FieldEndStories.addTo(registry);
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
        // Widgets/Inputs/RadioGroup
        RadioGroupStories.addTo(registry);
        // Widgets/Inputs/SuggestionBox
        SuggestionBoxStories.addTo(registry);
        // Widgets/Inputs/TextInput
        TextInputStories.addTo(registry);
        // Widgets/Inputs/TickBox
        TickBoxStories.addTo(registry);
        // Widgets/Inputs/ValueSpinner
        ValueSpinnerStories.addTo(registry);
        // Widgets/Overlays/Tooltip
        TooltipStories.addTo(registry);
        // Widgets/Overlays/TooltipWidget
        InfoTooltipStories.addTo(registry);
        // Widgets/Menu/MenuPanel
        MenuPanelStories.addTo(registry);
        // Widgets/Menu/MenuItemWidget
        MenuItemCellStories.addTo(registry);
        // Widgets/Menu/TextContextMenu
        TextContextMenuStories.addTo(registry);
        // Widgets/Popover/InfoPopoverCell
        InfoColumnStories.addTo(registry);
        // Widgets/Popover/Popover
        PopoverStories.addTo(registry);
        // Widgets/PopupPositioner/Popup contract
        PopupContractStories.addTo(registry);
        // Widgets/Feedback/SourceProgressBar
        SourceProgressBarStories.addTo(registry);
        // Widgets/Feedback/Spinner
        BusySpinnerStories.addTo(registry);
        // Widgets/Feedback/TaskCounter
        TaskCounterStories.addTo(registry);
        // Widgets/Layout/RoundedPanel
        RoundedPanelStories.addTo(registry);
        // Widgets/Layout/SplitPane
        SplitPaneStories.addTo(registry);
        SplitPaneResizingStories.addTo(registry);
        SplitPaneContentStories.addTo(registry);
        // Widgets/Layout/SplitPaneStates
        SplitPaneStateStories.addTo(registry);
        // Widgets/Inputs/ViewStates (GWT only): every control as normal, read only and disabled
        ViewStateStories.addTo(registry);
        return registry;
    }
}
