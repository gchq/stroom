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

package stroom.gwt.workbench.framework.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.TextResource;

/// The workbench framework's own markup and styles, compiled into the GWT module so that the
/// pages hosting the workbench don't need to provide them.
///
/// The CSS is held as text rather than a `CssResource` as GWT's CSS parser doesn't support
/// modern CSS such as custom properties and grid layouts.
public interface WorkbenchResources extends ClientBundle {

    /// The shared instance.
    WorkbenchResources INSTANCE = GWT.create(WorkbenchResources.class);

    /// @return The markup of the manager, i.e. the sidebar, toolbar and addon panel.
    @Source("workbench-manager.html")
    TextResource managerHtml();

    /// @return The styles for the manager.
    @Source("workbench-manager.css")
    TextResource managerCss();

    /// @return The SVG filters used to simulate vision deficiencies, from React Storybook's
    /// accessibility addon.
    @Source("vision-filters.svg")
    TextResource visionFiltersSvg();

    /// @return The styles for the preview.
    @Source("workbench-preview.css")
    TextResource previewCss();
}
