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

package stroom.cell.valuespinner.shared;

import stroom.util.shared.SharedLong;

public class EditableLong extends SharedLong implements Editable {
    private static final long serialVersionUID = -5468699731097143387L;

    private boolean editable = true;

    public EditableLong() {
    }

    public EditableLong(final Long _long) {
        super(_long);
    }

    @Override
    public boolean isEditable() {
        return editable;
    }

    @Override
    public void setEditable(final boolean editable) {
        this.editable = editable;
    }
}
