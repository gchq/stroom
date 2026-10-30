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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.selection.client.SuggestionBox;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.stream.Collectors;

/// Stories for Stroom's [SuggestionBox], a text field that suggests what to type (an expression
/// term's value suggests the field's values): an editable combobox with list autocomplete, whose
/// focus stays in the field. Here it suggests fruit, those starting with what was typed.
public final class SuggestionBoxStories {

    private static final String MAX_WIDTH = "300px";
    private static final List<String> FRUIT = List.of("apple", "apricot", "banana", "cherry", "date");
    // Told of Enter when the box doesn't take it for its list, as a term's value runs the query
    private static final String ON_ENTER = "onEnter";

    private SuggestionBoxStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/SuggestionBox", SuggestionBoxStories.class)
                .layout(StoryLayout.CENTERED)
                // Typing shows the suggestions for what was typed, below the field, which keeps the
                // focus; none is chosen yet
                .story("TypingSuggests", SuggestionBoxStories::render)
                .withPlay(play -> {
                    final Query field = play.getByRole("combobox", "Fruit");
                    play.type(field, "ap");
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(screen.getByRole("option", "apricot")).toBeInTheDocument());
                    play.expect(screen.getAllByRole("option")).toHaveLength(2);
                    play.expect(field).toHaveAttribute("aria-expanded", "true");
                    play.expect(field).toHaveAttribute("aria-autocomplete", "list");
                    play.expect(field).not().toHaveAttribute("aria-activedescendant");
                    play.expect(field).toHaveFocus();
                })
                // Down and Up move through the suggestions, which the field says
                // (aria-activedescendant); Enter puts the one moved to in the field and closes the
                // list, and isn't passed on (a term's value would run the query); Enter again is
                .story("KeysChooseASuggestion", SuggestionBoxStories::render)
                .withPlay(play -> {
                    final Query field = play.getByRole("combobox", "Fruit");
                    play.type(field, "ap");
                    final Play screen = play.screen();
                    final Query apple = screen.findByRole("option", "apple");
                    play.keyboard("{ArrowDown}");
                    play.expect(apple).toHaveAttribute("aria-selected", "true");
                    play.expect("the field's active option is apple", () ->
                            apple.element().get().getId().equals(
                                    field.element().get().getAttribute("aria-activedescendant"))).toBe(true);
                    play.keyboard("{ArrowDown}");
                    play.expect(screen.getByRole("option", "apricot")).toHaveAttribute("aria-selected", "true");
                    play.expect(apple).toHaveAttribute("aria-selected", "false");
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(screen.queryByRole("option", "apricot")).toBeNull());
                    play.expect(field).toHaveValue("apricot");
                    play.expect(field).toHaveFocus();
                    play.expect(field).toHaveAttribute("aria-expanded", "false");
                    play.expect(play.spy(InputWidgets.ON_CHANGE)).toHaveBeenCalledWith("apricot");
                    play.expect(play.spy(ON_ENTER)).not().toHaveBeenCalled();
                    play.keyboard("{Enter}");
                    play.expect(play.spy(ON_ENTER)).toHaveBeenCalled();
                })
                // Escape closes the list and keeps what was typed
                .story("EscapeKeepsWhatWasTyped", SuggestionBoxStories::render)
                .withPlay(play -> {
                    final Query field = play.getByRole("combobox", "Fruit");
                    play.type(field, "b");
                    final Play screen = play.screen();
                    screen.findByRole("option", "banana");
                    play.keyboard("{ArrowDown}");
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(screen.queryByRole("option", "banana")).toBeNull());
                    play.expect(field).toHaveValue("b");
                    play.expect(field).toHaveFocus();
                })
                // A click on the field shows the suggestions, and a click on one puts it in the
                // field, which keeps the focus
                .story("ClickChoosesASuggestion", SuggestionBoxStories::render)
                .withPlay(play -> {
                    final Query field = play.getByRole("combobox", "Fruit");
                    play.click(field);
                    final Play screen = play.screen();
                    play.click(screen.findByRole("option", "cherry"));
                    play.waitFor(() -> play.expect(field).toHaveValue("cherry"));
                    play.expect(field).toHaveFocus();
                    play.expect(screen.queryByRole("option", "cherry")).toBeNull();
                })
                // Text that matches no suggestion is kept: the suggestions are only hints
                .story("AnyTextIsKept", SuggestionBoxStories::render)
                .withPlay(play -> {
                    final Query field = play.getByRole("combobox", "Fruit");
                    play.type(field, "dragonfruit");
                    play.tab();
                    play.expect(field).toHaveValue("dragonfruit");
                    play.expect(play.spy(InputWidgets.ON_CHANGE)).toHaveBeenCalledWith("dragonfruit");
                    play.expect(play.screen().queryByRole("listbox")).toBeNull();
                });
    }

    private static Widget render(final StoryContext context) {
        final SuggestionBox box = new SuggestionBox();
        box.setAccessibleName("Fruit");
        // Answers at once, with the fruit that starts with what was typed
        box.setSource((text, consumer) -> consumer.accept(FRUIT.stream()
                .filter(fruit -> fruit.startsWith(text.trim().toLowerCase()))
                .collect(Collectors.toList())));
        final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
        box.addValueChangeHandler(event -> onChange.call(event.getValue()));
        final Spy onEnter = context.fn(ON_ENTER);
        box.addKeyDownHandler(event -> {
            if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
                onEnter.call();
            }
        });
        return InputWidgets.maxWidth(box, MAX_WIDTH);
    }
}
