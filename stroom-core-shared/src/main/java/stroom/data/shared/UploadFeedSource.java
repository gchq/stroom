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

package stroom.data.shared;

import stroom.docref.HasDisplayValue;

/// Where the feed for each uploaded stream comes from when uploading data through the UI.
public enum UploadFeedSource implements HasDisplayValue {
    /// Every stream goes into the feed selected in the upload dialog, whatever any
    /// `.meta` or `.manifest` entries in the uploaded file say.
    SELECTED_FEED("The feed I'm uploading to"),
    /// Each stream goes into the feed named in its own `.meta` or `.manifest` entry.
    /// Streams that don't name a feed go into the feed selected in the upload dialog.
    ZIP_FEEDS("The feeds named in the zip");

    private final String displayValue;

    UploadFeedSource(final String displayValue) {
        this.displayValue = displayValue;
    }

    /// @return The text to show for this choice in the upload dialog.
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
