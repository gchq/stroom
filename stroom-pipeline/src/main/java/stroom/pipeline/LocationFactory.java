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

package stroom.pipeline;

import stroom.util.shared.Location;

public interface LocationFactory {

    Location create(int colNo, int lineNo);

    Location create();

    /**
     * Create a {@link Location} from the passed {@link Location}, which allows the
     * {@link LocationFactory} implementation to add any location information in
     * addition to the line/col. If location is null, creates a new {@link Location}.
     */
    default Location create(final Location location) {
        if (location == null) {
            return create();
        } else {
            return create(location.getLineNo(), location.getColNo());
        }
    }
}
