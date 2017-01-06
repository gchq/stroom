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

package stroom.pool.shared;

import stroom.util.shared.SharedObject;

public class PoolRow implements SharedObject {
    private static final long serialVersionUID = -7367500560554774611L;

    private String poolName;

    public PoolRow() {
        // Default constructor necessary for GWT serialisation.
    }

    public PoolRow(final String poolName) {
        this.poolName = poolName;
    }

    public String getPoolName() {
        return poolName;
    }

    @Override
    public int hashCode() {
        return poolName.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof PoolRow)) {
            return false;
        }

        return ((PoolRow) obj).poolName.equals(poolName);
    }
}
