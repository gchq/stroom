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

package stroom.data.store.impl.fs.shared;

import stroom.docref.HasDisplayValue;

public enum FsVolumeType implements HasDisplayValue {
    STANDARD(0, "Standard"),
    S3(1, "S3");

    private static final FsVolumeType[] TYPES = {STANDARD, S3};

    private final int id;
    private final String displayValue;

    FsVolumeType(final int id,
                 final String displayValue) {
        this.id = id;
        this.displayValue = displayValue;
    }

    public static FsVolumeType fromId(final int id) {
        if (id < 0 || id > 1) {
            return STANDARD;
        }
        return TYPES[id];
    }

    public int getId() {
        return id;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
