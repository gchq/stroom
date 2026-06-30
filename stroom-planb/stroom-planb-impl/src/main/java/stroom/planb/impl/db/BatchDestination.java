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

package stroom.planb.impl.db;

import java.io.IOException;

/**
 * Transfers a completed batch from the local writer directory to its final
 * destination(s) and cleans up the writer directory on success.
 *
 * <p>Implementations must be thread-safe: the singleton {@link PlanBStreamWriterFactory}
 * may call {@link #publish} from multiple pipeline threads concurrently.
 * Thread-safety is natural because each {@link WrittenBatch} has its own writerDir.
 */
public interface BatchDestination {
    void publish(WrittenBatch batch) throws IOException;
}
