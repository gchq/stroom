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

package stroom.data.shared;

public enum DataType {
    /**
     * Data that has been demarcated into segments, e.g. cooked events
     */
    SEGMENTED,
    /**
     * Data that has not been demarcated into segments, i.e. raw un-cooked data.
     */
    NON_SEGMENTED,

    MARKER
}
