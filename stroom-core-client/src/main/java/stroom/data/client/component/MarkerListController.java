/*
 * Copyright 2016 Crown Copyright
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

package stroom.data.client.component;

import stroom.util.shared.Marker;
import stroom.util.shared.Severity;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// A stream's markers ([MarkerList]), grouped by severity, every group expanded at first. Its owner
/// fetches them, for the severities expanded, and is told when a group is expanded or collapsed.
public class MarkerListController extends Controller {

    private static final Set<Severity> ALL_SEVERITIES = new HashSet<>(Arrays.asList(Severity.SEVERITIES));

    private final MarkerList view;
    private Set<Severity> expandedSeverities = new HashSet<>(ALL_SEVERITIES);
    private Runnable onExpandedChange = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The content.
    @Inject
    public MarkerListController(final EventBus eventBus,
                                final MarkerList view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        view.setOnToggle(this::toggle);
    }

    /// Shows a page of markers.
    ///
    /// @param markers The page's markers, or `null` for none.
    /// @param start   The absolute index of the page's first marker.
    /// @param count   How many markers there are in all.
    public void setData(final List<Marker> markers, final int start, final int count) {
        view.setData(markers, start, count);
    }

    /// @return The severities whose groups are expanded, or `null` if none are.
    public Severity[] getExpandedSeverities() {
        if (expandedSeverities.isEmpty()) {
            return null;
        }
        return expandedSeverities.toArray(new Severity[0]);
    }

    /// Expands every group again.
    public void resetExpandedSeverities() {
        expandedSeverities = new HashSet<>(ALL_SEVERITIES);
    }

    /// @param onExpandedChange Run when a group is expanded or collapsed, to fetch the markers again.
    public void setOnExpandedChange(final Runnable onExpandedChange) {
        this.onExpandedChange = onExpandedChange;
    }

    private void toggle(final Severity severity) {
        if (severity != null) {
            if (expandedSeverities.contains(severity)) {
                expandedSeverities.remove(severity);
            } else {
                expandedSeverities.add(severity);
            }
        }
        onExpandedChange.run();
    }
}
