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

package stroom.entity.shared;

import stroom.util.shared.HasDisplayValue;

public enum EntityAction implements HasDisplayValue {
    ADD("Add"), EQUAL("Equal"), UPDATE("Update"), DELETE("Delete");

    private final String displayValue;

    EntityAction(final String displayValue) {
        this.displayValue = displayValue;
    }

    /**
     * @return string used in drop downs.
     */
    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
