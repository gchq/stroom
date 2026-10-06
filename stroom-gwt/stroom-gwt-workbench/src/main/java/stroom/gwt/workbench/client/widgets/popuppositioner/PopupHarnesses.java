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


package stroom.gwt.workbench.client.widgets.popuppositioner;

import stroom.annotation.client.SettingBlock;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeGroup;
import stroom.explorer.client.presenter.TypeFilterPresenter;
import stroom.explorer.client.presenter.TypeFilterViewImpl;
import stroom.explorer.shared.DocumentTypes;
import stroom.gwt.workbench.client.widgets.ContentPresenter;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.item.client.SelectionBox;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.form.client.FormGroup;
import stroom.widget.help.client.HelpButton;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.menu.client.presenter.SimpleMenuItem;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;

/// The real Stroom widgets that the popup contract stories open, each rendered closed, as the
/// React registry's harnesses render theirs.
final class PopupHarnesses {

    /// The time React's CustomDateBox harness starts at, 2026-08-12T00:00:00.000Z.
    private static final long DATE_MILLIS = 1786492800000L;
    // Where React's portal cascade harness puts its panels (position: fixed, so viewport pixels)
    private static final int OUTER_LEFT = 400;
    private static final int INNER_LEFT = 640;
    private static final int CASCADE_TOP = 40;

    private PopupHarnesses() {
        // Static utility
    }

    /// React's `<HelpButton heading content>`: Stroom's `HelpButton`, shown by `HelpManager`.
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget helpButton(final StoryPopups popups) {
        final HelpButton helpButton = HelpButton.create("Heading");
        helpButton.setHelpContent("Heading", SafeHtmlUtils.fromSafeConstant("<p>Help body text</p>"));
        return helpButton;
    }

    /// React's QuickFilter harness: Stroom's `QuickFilter`, whose help button shows its own popup.
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget quickFilter(final StoryPopups popups) {
        return new QuickFilter();
    }

    /// React's `<FormGroup label helpOverride>`: Stroom's `FormGroup`, whose help is a `HelpButton`.
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget formGroup(final StoryPopups popups) {
        final FormGroup formGroup = new FormGroup();
        formGroup.setIdentity("contract");
        formGroup.setLabel("A field");
        formGroup.overrideHelpText(SafeHtmlUtils.fromSafeConstant("<p>Field help</p>"));
        final TextBox textBox = new TextBox();
        formGroup.add(textBox);
        return formGroup;
    }

    /// React's TypeFilter harness: a button that shows Stroom's `TypeFilterPresenter`, as the
    /// explorer's filter button does.
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget typeFilter(final StoryPopups popups) {
        final TypeFilterPresenter presenter = new TypeFilterPresenter(popups.getEventBus(), new TypeFilterViewImpl());
        final List<DocumentType> types = Arrays.asList(
                new DocumentType(DocumentTypeGroup.DATA_PROCESSING, "Feed", "Feed", SvgImage.DOCUMENT_FEED),
                new DocumentType(DocumentTypeGroup.CONFIGURATION, "Dictionary", "Dictionary",
                        SvgImage.DOCUMENT_DICTIONARY));
        presenter.setDocumentTypes(new DocumentTypes(types, types));
        final Button button = triggerButton("Filter Types", "type-filter-trigger");
        // Stroom's filter button shows the presenter again to toggle it (PopupManager hides a
        // popup that is shown twice)
        button.addClickHandler(event -> presenter.show(button.getElement(), active -> {
            // The explorer updates its filter icon here
        }));
        return button;
    }

    /// React's `<SettingBlock renderEditor>`: Stroom's `SettingBlock`, whose click shows a chooser
    /// below it as `AnnotationEditPresenter.showStatusChooser` does.
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget settingBlock(final StoryPopups popups) {
        final SettingBlock settingBlock = new SettingBlock();
        settingBlock.add(new Label("Setting: current"));
        final ContentPresenter editor = ContentPresenter.html(popups.getEventBus(),
                "<div class=\"popover-body\">Editor body</div>");
        settingBlock.addClickHandler(event -> {
            final Element element = settingBlock.getElement();
            final PopupPosition popupPosition = new PopupPosition(element.getAbsoluteLeft() - 1,
                    element.getAbsoluteTop() + element.getClientHeight() + 2);
            ShowPopupEvent.builder(editor)
                    .popupType(PopupType.POPUP)
                    .popupPosition(popupPosition)
                    .addAutoHidePartner(element)
                    .fire();
        });
        return settingBlock;
    }

    /// React's SelectionBox harness: Stroom's `SelectionBox`.
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget selectionBox(final StoryPopups popups) {
        final SelectionBox<String> selectionBox = new SelectionBox<>();
        // Differs from React: Stroom's selection popup only has a quick filter (whose help popup
        // NestedRealWidgetsRouteEscapeToTheInnermost opens) for more than 10 items, so there are
        // 11, not React's three.
        selectionBox.addItems(new String[]{
                "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta", "Iota", "Kappa", "Lambda"});
        return selectionBox;
    }

    /// React's CustomDateBox harness: Stroom's `MyDateBox` (whose popup is `dateBoxPopup`).
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget dateBox(final StoryPopups popups) {
        final MyDateBox dateBox = new MyDateBox();
        dateBox.setUtc(true);
        dateBox.setMilliseconds(DATE_MILLIS);
        return dateBox;
    }

    /// React's `MenuHarness`: a button showing a menu nested two levels deep, as Stroom's toolbar
    /// buttons show theirs (`ShowMenuEvent` below the button, which is the auto-hide partner).
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget menu(final StoryPopups popups) {
        final List<Item> items = Arrays.asList(
                simple("Leaf one"),
                new SimpleParentMenuItem(1, SafeHtmlUtils.fromString("Parent"), null, Arrays.asList(
                        simple("Child one"),
                        new SimpleParentMenuItem(1, SafeHtmlUtils.fromString("Nested parent"), null, Arrays.asList(
                                simple("Grandchild one"),
                                simple("Grandchild two"))))));
        final Button button = triggerButton("Open menu", "menu-harness-trigger");
        button.addClickHandler(event -> ShowMenuEvent.builder()
                .items(items)
                .popupPosition(new PopupPosition(new Rect(button.getElement()), PopupLocation.BELOW))
                .addAutoHidePartner(button.getElement())
                .fire(popups.getEventBus()::fireEvent));
        return button;
    }

    /// React's `PortalCascadeHarness`: two popups, the child's trigger inside the parent's panel
    /// and its panel outside it (on the body, as every Stroom popup is). Each is shown with
    /// `ShowPopupEvent` as an auto-hiding `POPUP` with its trigger as the auto-hide partner, as
    /// `MenuPresenter` shows a submenu.
    ///
    /// @param popups The story's popups.
    /// @return The widget.
    static Widget portalCascade(final StoryPopups popups) {
        final ContentPresenter inner = new ContentPresenter(popups.getEventBus(),
                cascadePanel("cascade-inner-panel", "#444", new Label("inner body")));
        final Button innerTrigger = triggerButton("Inner", "cascade-inner-trigger");
        innerTrigger.addClickHandler(event -> showAt(inner, INNER_LEFT, innerTrigger));

        final ContentPresenter outer = new ContentPresenter(popups.getEventBus(),
                cascadePanel("cascade-outer-panel", "#333", new Label("outer body"), innerTrigger));
        final Button outerTrigger = triggerButton("Outer", "cascade-outer-trigger");
        outerTrigger.addClickHandler(event -> showAt(outer, OUTER_LEFT, outerTrigger));
        return outerTrigger;
    }

    private static void showAt(final ContentPresenter presenter, final int left, final Widget trigger) {
        ShowPopupEvent.builder(presenter)
                .popupType(PopupType.POPUP)
                .popupPosition(new PopupPosition(left, CASCADE_TOP))
                .addAutoHidePartner(trigger.getElement())
                .fire();
    }

    private static Widget cascadePanel(final String className, final String background, final Widget... widgets) {
        final FlowPanel panel = new FlowPanel();
        panel.addStyleName(className);
        final Style style = panel.getElement().getStyle();
        style.setProperty("padding", "8px");
        style.setProperty("background", background);
        for (final Widget widget : widgets) {
            panel.add(widget);
        }
        return panel;
    }

    private static Button triggerButton(final String text, final String className) {
        final Button button = new Button();
        button.setText(text);
        button.addStyleName(className);
        return button;
    }

    private static Item simple(final String text) {
        return new SimpleMenuItem.Builder()
                .text(text)
                .command(() -> {
                    // React's no-op command
                })
                .build();
    }
}
