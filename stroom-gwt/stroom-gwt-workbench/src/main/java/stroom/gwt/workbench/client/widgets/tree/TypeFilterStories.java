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

package stroom.gwt.workbench.client.widgets.tree;

import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.explorer.client.component.TypeFilter;
import stroom.explorer.client.component.TypeFilterController;
import stroom.explorer.shared.DocumentTypes;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.button.client.Button;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Stories for Stroom's [TypeFilterController]: the explorer's popup of document types to show, each
/// with a tick box, under an "All / None" row.
public final class TypeFilterStories {

    private static final String ON_INCLUDED_TYPES_CHANGE = "onIncludedTypesChange";
    private static final String ON_CLOSE = "onClose";
    // The document types the stories offer
    private static final String[] FIXTURE_DOC_TYPES = {
            "Folder", "Feed", "Pipeline", "XSLT", "Dashboard", "Query", "Dictionary", "Index"};

    private TypeFilterStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Tree/TypeFilter", TypeFilterStories.class)
                .layout(StoryLayout.CENTERED)
                // Toggle the popup and tick/untick document types (tri-state All/None row)
                .story("Basic", context -> typeFilter(context, null, false))
                // Pre-filtered to a subset, so the All/None row shows the half-tick state
                .story("PartialSelection", context -> typeFilter(context, Set.of("Feed", "Pipeline"), true))
                // The types are a list (listbox) whose options say whether they are ticked
                // (aria-checked, All / None part ticked when some are): the arrows move, Space or
                // Enter ticks or unticks the type with the focus, and typing goes to a type
                .story("Keys", context -> typeFilter(context, null, true))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Play types = screen.within(screen.findByRole("listbox", "Document types"));
                    final Query all = types.getByRole("option", "All / None");
                    final Query dashboard = types.getByRole("option", "Dashboard");
                    final Query xslt = types.getByRole("option", "XSL Translation");
                    play.expect(types.getAllByRole("option")).toHaveLength(FIXTURE_DOC_TYPES.length + 1);
                    play.expect(xslt).toBeVisible();
                    play.waitFor(() -> play.expect(all).toHaveFocus());
                    play.expect(all).toHaveAttribute("aria-checked", "true");
                    play.keyboard("{ArrowDown}");
                    play.expect(dashboard).toHaveFocus();
                    play.expect(dashboard).toHaveAttribute("aria-checked", "true");
                    play.keyboard(" ");
                    play.expect(dashboard).toHaveAttribute("aria-checked", "false");
                    play.expect(all).toHaveAttribute("aria-checked", "mixed");
                    play.expect(play.spy(ON_INCLUDED_TYPES_CHANGE)).toHaveBeenCalledTimes(1);
                    play.keyboard("x");
                    play.expect(xslt).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.expect(xslt).toHaveAttribute("aria-checked", "false");
                    play.expect(play.spy(ON_INCLUDED_TYPES_CHANGE)).toHaveBeenLastCalledWith(
                            "Included: Folder, Feed, Pipeline, Query, Dictionary, Index");
                });
    }

    private static Widget typeFilter(final StoryContext context,
                                        final Set<String> included,
                                        final boolean open) {
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .withoutStartupFixtures()
                .build();
        final TypeFilterController typeFilter = harness.own(
                new TypeFilterController(harness.getEventBus(), new TypeFilter()));
        final List<DocumentType> types = new ArrayList<>();
        for (final String type : FIXTURE_DOC_TYPES) {
            types.add(DocumentTypeRegistry.get(type));
        }
        typeFilter.setDocumentTypes(new DocumentTypes(types, types));
        if (included != null) {
            // Stroom starts with every type ticked, so untick the others
            for (final DocumentType type : types) {
                if (!included.contains(type.getType())) {
                    typeFilter.toggle(type);
                }
            }
        }

        // A text button, which shows how many types are chosen
        final Button button = new Button("Type filter");
        final Label echo = new Label();
        echo.getElement().getStyle().setProperty("fontSize", "12px");
        echo.getElement().getStyle().setProperty("marginTop", "12px");
        final boolean showCount = included == null;
        final Runnable update = () -> {
            final Optional<Set<String>> includedTypes = typeFilter.getIncludedTypes();
            final List<String> names = new ArrayList<>();
            for (final DocumentType type : types) {
                if (includedTypes.isPresent() && includedTypes.get().contains(type.getType())) {
                    names.add(type.getType());
                }
            }
            echo.setText("Included: " + (includedTypes.isEmpty()
                    ? "all"
                    : names.isEmpty()
                            ? "none"
                            : String.join(", ", names)));
            if (showCount) {
                button.setText("Type filter" + (includedTypes.isEmpty()
                        ? ""
                        : " (" + names.size() + ")"));
            }
        };
        update.run();

        final Spy onIncludedTypesChange = context.fn(ON_INCLUDED_TYPES_CHANGE);
        final Spy onClose = context.fn(ON_CLOSE);
        harness.addRegistration(typeFilter.addSelectionListener(event -> {
            update.run();
            onIncludedTypesChange.call(echo.getText());
        }));
        // As NavigationController shows it: by its button, hidden by clicking outside it or (here)
        // the button
        final boolean[] showing = {false};
        typeFilter.setOnHidden(() -> {
            showing[0] = false;
            onClose.call();
        });
        final Runnable show = () -> {
            showing[0] = true;
            typeFilter.show(button.getElement(), hasActiveFilter -> {
                // The tree is filtered by the popup's owner
            });
        };
        button.addClickHandler(event -> {
            if (showing[0]) {
                typeFilter.hide();
            } else {
                show.run();
            }
        });

        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("padding", "40px");
        panel.add(button);
        if (showCount) {
            panel.add(echo);
        }
        harness.add(panel);
        if (open) {
            // Shown once the button is on the page, to place the popup by it
            Scheduler.get().scheduleDeferred(show::run);
        }
        return harness.asWidget();
    }
}
