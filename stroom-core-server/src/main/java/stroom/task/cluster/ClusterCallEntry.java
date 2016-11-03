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

import java.io.Serializable;

/**
 * Class that holds the response or error and time from each node.
 */
public class ClusterCallEntry<T> implements Serializable {
    private static final long serialVersionUID = 5415741869457259603L;

    private T result;
    private Throwable error;
    private long timeMs;

    public ClusterCallEntry(final T result, final Throwable error, final long timeMs) {
        this.result = result;
        this.error = error;
        this.timeMs = timeMs;
    }

    public T getResult() {
        return result;
    }

    public Long getTimeMs() {
        return timeMs;
    }

    public Throwable getError() {
        return error;
    }

    @Override
    public String toString() {
        if (error == null) {
            return result + " " + timeMs;
        }
        if (result == null) {
            return error + " " + timeMs;
        }

        return result + " " + error + " " + timeMs;
    }
}
