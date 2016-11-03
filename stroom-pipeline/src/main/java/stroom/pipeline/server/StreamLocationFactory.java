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

package stroom.pipeline.server;

import stroom.util.shared.Location;
import stroom.util.shared.StreamLocation;

public class StreamLocationFactory implements LocationFactory {
    private long streamNo = 1;

    @Override
    public Location create(final int lineNo, final int colNo) {
        return new StreamLocation(streamNo, lineNo, colNo);
    }

    @Override
    public Location create() {
        return new StreamLocation(streamNo, -1, -1);
    }

    public void setStreamNo(final long streamNo) {
        this.streamNo = streamNo;
    }

    public long getStreamNo() {
        return streamNo;
    }
}
