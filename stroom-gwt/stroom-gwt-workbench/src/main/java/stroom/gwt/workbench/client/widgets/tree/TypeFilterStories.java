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
import stroom.explorer.client.presenter.TypeFilterPresenter;
import stroom.explorer.client.presenter.TypeFilterViewImpl;
import stroom.explorer.shared.DocumentTypes;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.popup.client.event.HidePopupEvent;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Stories for Stroom's [TypeFilterPresenter], matching `Widgets/Tree/TypeFilter` in the React
/// Storybook: the explorer's popup of document types to show, each with a tick box, under an
/// "All / None" row.
public final class TypeFilterStories {

    private static final String ON_INCLUDED_TYPES_CHANGE = "onIncludedTypesChange";
    private static final String ON_CLOSE = "onClose";
    // The React stories' FIXTURE_DOC_TYPES
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
                .story("PartialSelection", context -> typeFilter(context, Set.of("Feed", "Pipeline"), true));
    }

    private static Widget typeFilter(final StoryContext context,
                                        final Set<String> included,
                                        final boolean open) {
        final ScreenHarness harness = ScreenHarness.builder(context, RestFixtures.builder().build())
                .withoutStartupFixtures()
                .build();
        final TypeFilterPresenter presenter = new TypeFilterPresenter(harness.getEventBus(), new TypeFilterViewImpl());
        final List<DocumentType> types = new ArrayList<>();
        for (final String type : FIXTURE_DOC_TYPES) {
            types.add(DocumentTypeRegistry.get(type));
        }
        presenter.setDocumentTypes(new DocumentTypes(types, types));
        if (included != null) {
            // Stroom starts with every type ticked, so untick the others
            for (final DocumentType type : types) {
                if (!included.contains(type.getType())) {
                    presenter.execute(type);
                }
            }
        }

        // Differs from React: a GWT button with the React story's `icon-button` class
        final Button button = new Button("Type filter");
        button.setStyleName("icon-button");
        final Label echo = new Label();
        echo.getElement().getStyle().setProperty("fontSize", "12px");
        echo.getElement().getStyle().setProperty("marginTop", "12px");
        final boolean showCount = included == null;
        final Runnable update = () -> {
            final Optional<Set<String>> includedTypes = presenter.getIncludedTypes();
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
        harness.addRegistration(presenter.addDataSelectionHandler(event -> {
            update.run();
            onIncludedTypesChange.call(echo.getText());
        }));
        // As NavigationPresenter shows it: by its button, hidden by clicking outside it or (here,
        // as the React story toggles it) the button
        final boolean[] showing = {false};
        harness.addRegistration(harness.getEventBus().addHandler(HidePopupEvent.getType(), event -> {
            if (event.getPresenterWidget() == presenter) {
                showing[0] = false;
                onClose.call();
            }
        }));
        final Runnable show = () -> {
            showing[0] = true;
            presenter.show(button.getElement(), hasActiveFilter -> {
                // The tree is filtered by the popup's owner
            });
        };
        button.addClickHandler(event -> {
            if (showing[0]) {
                presenter.escape();
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
