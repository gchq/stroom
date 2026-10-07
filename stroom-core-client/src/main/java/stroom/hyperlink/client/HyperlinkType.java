/*
 * Copyright 2017 Crown Copyright
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

package stroom.hyperlink.client;

public enum HyperlinkType {
    /// Open the URL in a Stroom tab
    TAB,
    /// Open the URL in an iFrame dialog
    DIALOG,
    /// Open the URL as a Dashboard
    BROWSER,
    /// Open the URL in a new browser tab
    DASHBOARD,
    /// Opens the stepper using the URL parameters
    STEPPING,
    /// Opens the data view using the URL parameters
    DATA,
    /// Opens or creates the annotation view using the URL parameters
    ANNOTATION,
    ;
}
