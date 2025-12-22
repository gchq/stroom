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

package stroom.util.logging;

import java.io.Serializable;

/**
 * Utility used in the logging code to output human readable progress for zero
 * based index processing. e.g. size = 10, index = 0, outputs "1/10"
 */
public class LogItemProgress implements Serializable {

    private static final long serialVersionUID = -8931028520798738334L;

    private long pos = 0;
    private long size = 0;

    public LogItemProgress(final long pos, final long size) {
        this.pos = pos;
        this.size = size;
    }

    public void incrementProgress() {
        pos++;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append(pos);
        builder.append("/");
        builder.append(size);
        return builder.toString();
    }

}
