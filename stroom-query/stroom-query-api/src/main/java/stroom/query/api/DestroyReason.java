/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.api;

public enum DestroyReason {
    /**
     * Manual destruction via the search result store management UI.
     */
    MANUAL,
    /**
     * A result store that is no longer needed as a new search has started that will replace previous results.
     */
    NO_LONGER_NEEDED,
    /**
     * A Stroom tab has been closed that might result in the destruction of search results depending on the result store
     * settings.
     */
    TAB_CLOSE,
    /**
     * A browser window or tab has been closed that might result in the destruction of search results depending on the
     * result store settings.
     */
    WINDOW_CLOSE
}
