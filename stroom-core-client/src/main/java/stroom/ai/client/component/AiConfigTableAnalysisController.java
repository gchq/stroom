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

package stroom.ai.client.component;

import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// The Table Analysis tab of the Ask Stroom AI settings ([AiConfigTableAnalysis]), shown in the
/// settings dialog as its General tab is.
public class AiConfigTableAnalysisController extends Controller {

    private final AiConfigTableAnalysis view;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The tab.
    @Inject
    public AiConfigTableAnalysisController(final EventBus eventBus, final AiConfigTableAnalysis view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
    }

    /// Shows the settings, using the defaults for any the settings don't have.
    ///
    /// @param config    The table analysis settings, or null for the defaults.
    /// @param askConfig The settings they are part of, for the attachment download timeout, or
    ///                  null for its default.
    public void read(final TableAnalysisConfig config, final AskStroomAiConfig askConfig) {
        view.read(config, askConfig);
    }

    /// @return The table analysis settings shown.
    public TableAnalysisConfig write() {
        return view.write();
    }

    /// @return The time, in milliseconds, to wait for attached table data to download.
    public long getAttachmentDownloadTimeoutMs() {
        return view.getAttachmentDownloadTimeoutMs();
    }
}
