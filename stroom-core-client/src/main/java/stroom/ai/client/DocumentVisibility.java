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

package stroom.ai.client;

/**
 * Whether the browser tab is being looked at, so polling can slow down when it is not.
 */
final class DocumentVisibility {

    private DocumentVisibility() {
        // Static only.
    }

    /**
     * @return True if the browser tab is hidden (e.g. another tab is selected, or the window is minimised).
     */
    static native boolean isHidden() /*-{
        return $doc.hidden === true;
    }-*/;
}
