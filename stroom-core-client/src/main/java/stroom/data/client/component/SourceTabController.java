/*
 * Copyright 2020 Crown Copyright
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

import stroom.content.client.component.ContentTabController;
import stroom.data.client.SourceKey;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows a stream's source as a tab of its own.
public class SourceTabController extends ContentTabController {

    public static final String TAB_TYPE = "DataSource";

    private final SourceViewerController sourceController;
    private SourceKey sourceKey;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus         The application's event bus.
    /// @param sourceController The source controller.
    @Inject
    public SourceTabController(final EventBus eventBus,
                               final SourceViewerController sourceController) {
        super(eventBus);
        own(sourceController);
        this.sourceController = sourceController;

        initWidget(sourceController.asWidget());
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.FILE_RAW;
    }

    @Override
    public String getLabel() {
        if (sourceKey == null) {
            return "Source Data";
        }
        final String type = sourceKey.getOptChildStreamType()
                .map(val -> " (" + val + ")")
                .orElse("");
        return "Stream " + sourceKey.getMetaId()
               + " : " + (sourceKey.getPartIndex() + 1) // to one based
               + " : " + (sourceKey.getRecordIndex().orElse(0L) + 1) // to one based
               + type;
    }

    /// Shows a stream's source, at the start of the part highlighted.
    ///
    /// @param sourceLocation The place and its highlight.
    public void setSourceLocationUsingHighlight(final SourceLocation sourceLocation) {
        sourceKey = new SourceKey(sourceLocation);
        sourceController.setSourceLocationUsingHighlight(sourceLocation);
    }

    /// Shows a stream's source, from a place in it.
    ///
    /// @param sourceLocation The place.
    public void setSourceLocation(final SourceLocation sourceLocation) {
        sourceKey = new SourceKey(sourceLocation);
        sourceController.setSourceLocation(sourceLocation);
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
