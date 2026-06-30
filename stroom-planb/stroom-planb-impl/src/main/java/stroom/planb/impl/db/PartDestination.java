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

import stroom.meta.shared.Meta;

/**
 * Transfers the locally-written LMDB output for one pipeline part to its
 * remote processing destination.
 *
 * <p>Implementations are stateless singletons; all per-transfer state is
 * carried by {@link WrittenPart} and {@link Meta}.
 *
 * <p>Implementations must be thread-safe.
 */
public interface PartDestination {

    /**
     * Transfers the locally-written LMDB data for {@code part} to its
     * configured remote destination.
     *
     * @return {@code true} if the transfer succeeded; {@code false} on failure.
     *         On failure the caller retains the writer directory for operator
     *         inspection.
     */
    boolean transfer(WrittenPart part, Meta meta);
}
