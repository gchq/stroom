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

package stroom.query.shared;

import stroom.util.shared.OffsetRange;

public class VisResultRequest extends ComponentResultRequest {
    private static final long serialVersionUID = 8683770109061652092L;

    private VisDashboardSettings visDashboardSettings;
    private OffsetRange<Integer> requestedRange = new OffsetRange<Integer>(0, 100);

    public VisResultRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public VisResultRequest(final int offset, final int length) {
        requestedRange = new OffsetRange<Integer>(offset, length);
    }

    public VisDashboardSettings getVisDashboardSettings() {
        return visDashboardSettings;
    }

    public void setVisDashboardSettings(final VisDashboardSettings visDashboardSettings) {
        this.visDashboardSettings = visDashboardSettings;
    }

    public OffsetRange<Integer> getRequestedRange() {
        return requestedRange;
    }

    public void setRange(final int offset, final int length) {
        requestedRange = new OffsetRange<Integer>(offset, length);
    }

    @Override
    public ComponentType getComponentType() {
        return ComponentType.VIS;
    }
}
