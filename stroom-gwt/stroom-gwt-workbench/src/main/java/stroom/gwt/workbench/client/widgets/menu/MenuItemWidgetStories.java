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


package stroom.gwt.workbench.client.widgets.menu;

import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.args.ArgType;
import stroom.gwt.workbench.framework.client.args.Args;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuPresenter;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/// Stories for the rows of Stroom's menus (`MenuItemCell`), matching
/// `Widgets/Menu/MenuItemWidget` in the React Storybook.
///
/// Differs from React: Stroom's menu rows are cells of a menu's cell table, not widgets, so each
/// story shows a [MenuPresenter]'s view, with the rows, in place rather than as a popup.
public final class MenuItemWidgetStories {

    private static final String LABEL = "label";

    private MenuItemWidgetStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's iconHtml, shortcut, enabled and onClick props are shown by the Basic story; a
        // Stroom row's shortcut comes from its action's key binding
        registry.component("Widgets/Menu/MenuItemWidget", MenuItemWidgetStories.class)
                .layout(StoryLayout.CENTERED)
                .argType(ArgType.text(LABEL).description("The row's text."))
                .args(Args.of(LABEL, "Menu item"))
                // A small menu with icons, shortcuts, and a disabled item
                .story("Basic", context -> {
                    final Label last = new Label("Last: —");
                    final Style style = last.getElement().getStyle();
                    style.setProperty("padding", "4px 12px");
                    style.setProperty("fontSize", "12px");
                    // Differs from React: no shortcuts are shown, as Stroom's EDIT action has no
                    // key binding and there is no copy action
                    final Widget menu = menu(context, Arrays.asList(
                            MenuWidgets.icon("Edit", SvgImage.EDIT, () -> last.setText("Last: edit"))
                                    .action(Action.EDIT)
                                    .build(),
                            MenuWidgets.icon("Copy", SvgImage.COPY, () -> last.setText("Last: copy")).build(),
                            MenuWidgets.iconDisabled("Delete", SvgImage.DELETE).build()));
                    final FlowPanel box = new FlowPanel();
                    final Style boxStyle = box.getElement().getStyle();
                    boxStyle.setProperty("minWidth", "220px");
                    boxStyle.setProperty("border", "1px solid var(--panel__border-color)");
                    boxStyle.setProperty("padding", "4px 0");
                    box.add(menu);
                    box.add(last);
                    return box;
                })
                // A bare item with no icon or shortcut
                .story("Plain", context -> menu(context, Collections.singletonList(
                        MenuWidgets.simple(context.getArgs().getString(LABEL, ""), () -> {
                            // No command in the React story
                        }))))
                .withArgs(Args.of(LABEL, "Just a label"));
    }

    private static Widget menu(final StoryContext context, final List<Item> items) {
        final StoryPopups popups = StoryPopups.create(context);
        final MenuPresenter presenter = popups.createMenuPresenter();
        presenter.setData(items);
        presenter.bind();
        popups.addCleanUp(presenter::unbind);
        return presenter.getWidget();
    }
}
