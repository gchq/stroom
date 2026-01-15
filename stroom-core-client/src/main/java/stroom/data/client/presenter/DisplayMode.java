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

package stroom.data.client.presenter;

public enum DisplayMode {
    /**
     * Displays the presenter in a popup modal dialog
     */
    DIALOG("dialog"),
    /**
     * Displays the presenter in a top level stroom tab (not a browser tab)
     */
    STROOM_TAB("tab");

    private final String name;

    DisplayMode(final String name) {
        this.name = name;
    }

    public static DisplayMode parse(final String name) {

        for (final DisplayMode displayMode : DisplayMode.values()) {
            if (displayMode.name.equals(name)) {
                return displayMode;
            }
        }
        throw new IllegalArgumentException("Unknown displayMode name" + name);
    }

    public String getName() {
        return name;
    }
}
