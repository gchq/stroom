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

package stroom.gwt.workbench.client.app.main;

import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.stepping.client.presenter.SteppingFilterPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// Stories of `App/Main/StepFilterDialog`, showing Stroom's real [SteppingFilterPresenter] (the
/// 'Change Step Filters' dialog, as `SteppingPresenter` shows it).
///
/// The element icons come from `GET /pipeline/v1/propertyTypes`. The `onApply` spy records what the
/// dialog's consumer is given (the element id → settings map), described as
/// `element: n filter(s) TYPES`.
public final class StepFilterDialogStories {

    /// The name of the spy recording the settings applied (the dialog's consumer).
    static final String ON_APPLY = "onApply";

    // The element chooser's cell class for an element with active filters
    private static final String FILTER_ON = ".pipelineElementChooser-filterOn";

    private static final RestFixtures FIXTURES = RestFixtures.builder()
            .get(PipelineFixtures.PROPERTY_TYPES_PATH, RestReply.json(PipelineFixtures.PROPERTY_TYPES))
            .build();

    private StepFilterDialogStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/StepFilterDialog", StepFilterDialogStories.class)
                // Add an XPath filter to an element; the active indicator and per-element map track it
                .story("AddFilter", StepFilterDialogStories::render)
                .withPlay(play -> {
                    final Play screen = play.screen();
                    screen.findByText("Change Step Filters", StroomDom.DIALOG_TITLE);
                    // No active filters yet.
                    // The 'Has active filter(s)' icon is in every element's row, shown by the cell's
                    // 'filterOn' class (hidden by 'filterOff')
                    play.expect(screen.querySelector(FILTER_ON)).toBeNull();
                    // Add an XPath filter: the default match type (Exists) needs no value
                    play.click(screen.getByTitle("Add XPath Filter"));
                    final Play addDialog = dialog(screen, "Add XPath Filter");
                    play.click(addDialog.getByRole("button", StroomDom.button("OK")));
                    // The filter is listed and the element shows the active indicator
                    final Play xpathList = screen.within(screen.querySelector("#steppingConditions"));
                    play.waitFor(() -> play.expect(xpathList.getByText("Exists")).toBeInTheDocument());
                    play.waitFor(() -> play.expect(screen.querySelector(FILTER_ON)).not().toBeNull());
                    for (final String heading : new String[]{"XPath", "Condition", "Value", "Ignore Case"}) {
                        play.expect(xpathList.getByRole("columnheader", heading)).toBeInTheDocument();
                    }
                    // Per element: xmlWriter has no filters; switching back keeps xsltFilter's.
                    // An empty filter list shows no text, so the list is checked for having no 'Exists'
                    // row
                    final Play elements = screen.within(screen.querySelector(".pipelineElementChooser"));
                    play.click(elements.getByText("xmlWriter"));
                    play.waitFor(() -> play.expect(xpathList.queryByText("Exists")).toBeNull());
                    play.click(elements.getByText("xsltFilter"));
                    play.waitFor(() -> play.expect(xpathList.getByText("Exists")).toBeInTheDocument());
                    // OK applies the map with the element's filter
                    play.click(dialog(screen, "Change Step Filters").getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.spy(ON_APPLY)).toHaveBeenCalledTimes(1));
                    play.expect(play.spy(ON_APPLY)).toHaveBeenCalledWith("xsltFilter: 1 filter(s) EXISTS");
                    // The dialog closes itself on OK
                    play.waitFor(() -> play.expect(screen.queryByText("Change Step Filters", StroomDom.DIALOG_TITLE))
                            .toBeNull());
                    play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
                    play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
                });
    }

    private static Play dialog(final Play screen, final String caption) {
        return screen.within(screen.findByText(caption, StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
    }

    // Describes the elements with filters, e.g. 'xsltFilter: 1 filter(s) EXISTS'
    private static String describe(final Map<String, SteppingFilterSettings> settingsMap) {
        final List<String> descriptions = new ArrayList<>();
        settingsMap.forEach((elementId, settings) -> {
            final List<XPathFilter> filters = settings == null
                    ? null
                    : settings.getFilters();
            if (filters != null && !filters.isEmpty()) {
                final StringBuilder sb = new StringBuilder(elementId).append(": ").append(filters.size())
                        .append(" filter(s)");
                for (final XPathFilter filter : filters) {
                    sb.append(' ').append(filter.getMatchType().name());
                }
                descriptions.add(sb.toString());
            }
        });
        return String.join(", ", descriptions);
    }

    private static Widget render(final StoryContext context) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, FIXTURES)
                .injector(injector)
                .build();
        final Spy onApply = harness.fn(ON_APPLY);
        final List<PipelineElement> elements = List.of(
                new PipelineElement("xsltFilter", "XSLTFilter"),
                new PipelineElement("xmlWriter", "XMLWriter"));
        final SteppingFilterPresenter presenter = injector.getSteppingFilterPresenter();
        harness.afterStartUp(() -> presenter.show(elements, elements.get(0), new HashMap<>(), settings ->
                onApply.call(describe(settings))));
        return harness.asWidget();
    }
}
