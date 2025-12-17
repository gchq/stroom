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

package stroom.security.shared;

import stroom.docref.HasDisplayValue;

import java.util.ArrayList;
import java.util.List;

public enum PermissionShowLevel implements HasDisplayValue {
    SHOW_EXPLICIT("Show Explicit"),
    SHOW_EFFECTIVE("Show Effective"),
    SHOW_ALL("Show All");

    public static final List<PermissionShowLevel> ITEMS = new ArrayList<>();

    static {
        ITEMS.add(SHOW_EXPLICIT);
        ITEMS.add(SHOW_EFFECTIVE);
        ITEMS.add(SHOW_ALL);
    }

    private final String displayValue;

    PermissionShowLevel(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
