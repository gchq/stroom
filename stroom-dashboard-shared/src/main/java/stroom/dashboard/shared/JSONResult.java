/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.shared;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;

public class JSONResult implements SharedObject {
    private static final long serialVersionUID = -2964122512841756795L;

    private String json;

    public JSONResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public JSONResult(final String json) {
        this.json = json;
    }

    public String getJSON() {
        return json;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof JSONResult)) {
            return false;
        }

        final JSONResult result = (JSONResult) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(json, result.json);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(json);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return json;
    }
}
