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

package stroom.task.cluster;

public class CollectorIdImpl implements CollectorId {
    private static final long serialVersionUID = 9009491007920826639L;

    private String id;

    /**
     * Do not use this constructor it is for GWT serialisation only.
     */
    public CollectorIdImpl() {
    }

    /**
     * Do not use this constructor directly, instead please use
     * CollectorIdFactory.
     *
     * @param id
     *            The id of this collector.
     */
    public CollectorIdImpl(final String id) {
        this.id = id;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof CollectorIdImpl)) {
            return false;
        }

        final CollectorIdImpl collectorIdImpl = (CollectorIdImpl) o;
        return id.equals(collectorIdImpl.id);
    }

    @Override
    public String toString() {
        return "{" + id + "}";
    }
}
