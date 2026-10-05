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

import stroom.gwt.workbench.client.widgets.ButtonStories;
import stroom.gwt.workbench.client.widgets.TickBoxStories;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

/// Registers the GWT ports of the React Storybook's `Widgets/*` stories (77 components,
/// 308 stories in the React Storybook), in the React sidebar's order.
///
/// ## Adding a story class
///
/// 1. Write the stories class in the `stroom.gwt.workbench.client.widgets` package (see
///    `widgets/ButtonStories` for an example), giving it a
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
        // Widgets/Editors & Viewers/MarkdownEditor
        // Widgets/Editors & Viewers/SourcePresenter
        // Widgets/Editors & Viewers/SourceViewer
        // Widgets/Editors & Viewers/TextPresenter
        // Widgets/Editors & Viewers/XsdBrowser
        // Widgets/AceEditor/format
        // Widgets/ActionMenuCell
        // Widgets/Dialogs/AlertDialog
        // Widgets/Dialogs/Dialog
        // Widgets/Dialogs/DialogTitle
        // Widgets/Dialogs/Modal
        // Widgets/Dialogs/ExplorerDropdownPopup
        // Widgets/Dialogs/PromptDialog
        // Widgets/Dialogs/StaticDialog
        // Widgets/Dialogs/TextBoxPopupDialog
        // Widgets/Buttons/Button
        ButtonStories.addTo(registry);
        // Widgets/Buttons/IconButton
        // Widgets/Buttons/StepControls
        // Widgets/Date & Time/CalendarGrid
        // Widgets/Date & Time/CustomDateBox
        // Widgets/Date & Time/DateTimePicker
        // Widgets/Date & Time/DurationPicker
        // Widgets/Date & Time/TimePicker
        // Widgets/CellList
        // Widgets/Display/ClassificationLabel
        // Widgets/Display/ClassificationWrapper
        // Widgets/Display/CopyText
        // Widgets/Display/HtmlPresenter
        // Widgets/Display/InfinityLogo
        // Widgets/Display/Logo
        // Widgets/formatDateTime
        // Widgets/Tabs/CurveTabBar
        // Widgets/Tabs/LinkTabPanel
        // Widgets/Tabs/LinkTabsWidget
        // Widgets/Data Grid/DataGrid
        // Widgets/Cell Renderers/DocRefCell
        // Widgets/Cell Renderers/DocumentTypeCell
        // Widgets/Cell Renderers/ExpanderCell
        // Widgets/Cell Renderers/FeedRefCell
        // Widgets/Cell Renderers/SvgCell
        // Widgets/Cell Renderers/UserRefCell
        // Widgets/Selectors/DocSelectionBox
        // Widgets/Selectors/DropDownSelector
        // Widgets/Selectors/SelectionBox
        // Widgets/Selectors/UserRefSelectionBox
        // Widgets/Tree/EntityCheckList
        // Widgets/Tree/ExplorerTree
        // Widgets/Tree/TypeFilter
        // Widgets/Tree/HTree
        // Widgets/EntityCheckTree
        // Widgets/Query/ExpressionBuilder
        // Widgets/Inputs/FileChooser
        // Widgets/Inputs/FormGroup
        // Widgets/Inputs/LineColInput
        // Widgets/Inputs/PasswordInput
        // Widgets/Inputs/QuickFilter
        // Widgets/Inputs/QuickFilterPanel
        // Widgets/Inputs/SingleLineEditor
        // Widgets/Inputs/TextInput
        // Widgets/Inputs/TickBox
        TickBoxStories.addTo(registry);
        // Widgets/Inputs/ValueSpinner
        // Widgets/Glass/Drag guard contract
        // Widgets/Overlays/HelpButton
        // Widgets/Overlays/Tooltip
        // Widgets/Overlays/TooltipWidget
        // Widgets/Menu/MenuPanel
        // Widgets/Menu/MenuItemWidget
        // Widgets/Popover/InfoPopoverCell
        // Widgets/Popover/Popover
        // Widgets/PopupPositioner/Popup contract
        // Widgets/Feedback/ProgressBar
        // Widgets/Feedback/Spinner
        // Widgets/Feedback/TaskCounter
        // Widgets/Layout/RoundedPanel
        // Widgets/Layout/SplitLayoutPanel
        // Widgets/Layout/ThinSplitLayoutPanel
        return registry;
    }
}
