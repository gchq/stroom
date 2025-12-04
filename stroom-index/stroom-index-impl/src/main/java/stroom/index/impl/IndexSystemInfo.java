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

package stroom.index.impl;

import stroom.index.shared.IndexShard;
import stroom.index.shared.LuceneVersion;
import stroom.index.shared.LuceneVersionUtil;
import stroom.node.api.NodeInfo;
import stroom.util.shared.NullSafe;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provides system information for inspecting index shards.
 * Gets counts of documents grouped by stream id.
 * Requires the shardId as a query parameter.
 */
@Singleton
public class IndexSystemInfo implements HasSystemInfo {

    private static final String PARAM_NAME_STREAM_ID = "streamId";
    private static final String PARAM_NAME_LIMIT = "limit";
    private static final String PARAM_NAME_SHARD_ID = "shardId";

    private final LuceneProviderFactory luceneProviderFactory;
    private final IndexShardDao indexShardDao;
    private final NodeInfo nodeInfo;

    @Inject
    public IndexSystemInfo(final IndexShardDao indexShardDao,
                           final NodeInfo nodeInfo,
                           final LuceneProviderFactory luceneProviderFactory) {
        this.indexShardDao = indexShardDao;
        this.nodeInfo = nodeInfo;
        this.luceneProviderFactory = luceneProviderFactory;
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        throw new BadRequestException(
                "You must provide a shard ID using the query parameter " + PARAM_NAME_SHARD_ID);
    }

    @Override
    public SystemInfoResult getSystemInfo(final Map<String, String> params) {
        final int limit = NullSafe.getOrElse(
                params,
                map -> map.get(PARAM_NAME_LIMIT),
                Integer::valueOf,
                10_000);
        final Long requestedStreamId = NullSafe.get(params, map -> map.get(PARAM_NAME_STREAM_ID), Long::valueOf);
        final long shardId = NullSafe.getAsOptional(
                        params,
                        map -> map.get(PARAM_NAME_SHARD_ID),
                        Long::parseLong)
                .orElseThrow(() ->
                        new BadRequestException(
                                "You must provide a shard ID using the query parameter " + PARAM_NAME_SHARD_ID));
        return getSystemInfo(shardId, limit, requestedStreamId);
    }

    @Override
    public List<ParamInfo> getParamInfo() {
        return List.of(
                ParamInfo.mandatoryParam(
                        PARAM_NAME_SHARD_ID,
                        "The id of the index shard to inspect."),
                ParamInfo.optionalParam(
                        PARAM_NAME_STREAM_ID,
                        "The id of a specific stream to query for."),
                ParamInfo.optionalParam(PARAM_NAME_LIMIT,
                        "A limit on the number of docs to return")
        );
    }

    private SystemInfoResult getSystemInfo(final long shardId,
                                           final Integer limit,
                                           final Long streamId) {
        try {
            final Optional<IndexShard> optional = indexShardDao.fetch(shardId);
            if (optional.isEmpty()) {
                throw new RuntimeException("Unknown shardId " + shardId);
            }
            final IndexShard indexShard = optional.get();
            if (doesThisNodeOwnTheShard(indexShard)) {
                final LuceneVersion luceneVersion = LuceneVersionUtil.getLuceneVersion(indexShard.getIndexVersion());
                return luceneProviderFactory.get(luceneVersion)
                        .getIndexSystemInfoProvider()
                        .getSystemInfo(indexShard, limit, streamId);
            } else {
                return SystemInfoResult.builder(this)
                        .addDetail("ShardId", indexShard.getId())
                        .addDetail("ThisNode", nodeInfo.getThisNodeName())
                        .addDetail("OwningNode", indexShard.getNodeName())
                        .addDetail("Owned", false)
                        .build();
            }
        } catch (final RuntimeException e) {
            return SystemInfoResult.builder(this)
                    .addError(e)
                    .build();
        }
    }

    private boolean doesThisNodeOwnTheShard(final IndexShard indexShard) {
        final String thisNodeName = nodeInfo.getThisNodeName();
        final String shardNodeName = indexShard.getNodeName();
        return thisNodeName.equals(shardNodeName);
    }
}
