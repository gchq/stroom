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

package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.ProcessingInfoResponse;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.searchable.api.Searchable;
import stroom.util.time.StroomDuration;

import java.util.List;

public interface ReferenceDataService extends Searchable {

    List<RefStoreEntry> entries(int limit);

    List<RefStoreEntry> entries(final int limit,
                                final Long refStreamId,
                                final String mapName);

    List<ProcessingInfoResponse> refStreamInfo(int limit);

    List<ProcessingInfoResponse> refStreamInfo(final int limit,
                                               final Long refStreamId,
                                               final String mapName);

    String lookup(final RefDataLookupRequest refDataLookupRequest);

    void purge(final StroomDuration stroomDuration, final String nodeName);

    void purge(final String feedName,
               final StroomDuration stroomDuration,
               final String nodeName);

    /**
     * Purge the specified ref stream on all nodes. Will not error if the stream is not found.
     */
    void purge(final long refStreamId, final String nodeName);

    void clearBufferPool(final String nodeName);
}
