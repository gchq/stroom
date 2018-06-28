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

package stroom.util.shared;

import stroom.docref.SharedObject;

public class SharedBoolean implements SharedObject {
    private static final long serialVersionUID = 2999109513859666073L;

    private Boolean _boolean;

    public SharedBoolean() {
        // Default constructor necessary for GWT serialisation.
    }

    public SharedBoolean(final Boolean _boolean) {
        this._boolean = _boolean;
    }

    public static SharedBoolean wrap(final Boolean _boolean) {
        if (_boolean == null) {
            return null;
        }
        return new SharedBoolean(_boolean);
    }

    public Boolean getBoolean() {
        return _boolean;
    }

    public void setBoolean(Boolean boolean1) {
        _boolean = boolean1;
    }

    @Override
    public String toString() {
        return String.valueOf(_boolean);
    }
}
