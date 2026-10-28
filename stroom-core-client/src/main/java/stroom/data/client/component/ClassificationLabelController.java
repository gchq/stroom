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

package stroom.data.client.component;

import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Gives a [ClassificationLabel] the classification colours configured in the UI config
/// (`theme.labelColours`, e.g. `OFFICIAL=#00ff00,SECRET=#ff0000`).
///
/// ```java
/// make(classificationLabelControllerProvider).attach(view.getClassificationLabel());
/// ```
public class ClassificationLabelController extends Controller {

    private final UiConfigCache uiConfigCache;

    /// Made by GIN, with the UI config the colours are read from.
    ///
    /// @param uiConfigCache The UI's configuration.
    @Inject
    public ClassificationLabelController(final UiConfigCache uiConfigCache) {
        this.uiConfigCache = uiConfigCache;
    }

    /// Attaches this to the label it colours, giving it the colours once the UI config is read.
    ///
    /// @param label The label, e.g. one a template declares.
    public void attach(final ClassificationLabel label) {
        Objects.requireNonNull(label, "label");
        uiConfigCache.get(guard(config -> {
            if (config != null) {
                label.setLabelColours(parseLabelColours(config.getTheme().getLabelColours()));
            }
        }), new DefaultTaskMonitorFactory(label));
    }

    /// Reads the configured label colours.
    ///
    /// @param csv The colours, `NAME=colour` pairs separated by commas, or null for none.
    /// @return Each colour by its name in upper case, in the order given; pairs that aren't one
    ///         name and one colour are left out.
    static Map<String, String> parseLabelColours(final String csv) {
        if (csv == null) {
            return Collections.emptyMap();
        }
        final Map<String, String> labelColours = new LinkedHashMap<>();
        for (final String part : csv.split(",")) {
            final String[] kv = part.split("=");
            if (kv.length == 2) {
                labelColours.putIfAbsent(kv[0].toUpperCase(Locale.ROOT), kv[1]);
            }
        }
        return labelColours;
    }
}
