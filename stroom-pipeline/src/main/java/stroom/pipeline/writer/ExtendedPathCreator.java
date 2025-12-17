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

package stroom.pipeline.writer;

import stroom.node.api.NodeInfo;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.SearchIdHolder;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ExtendedPathCreator extends SimplePathCreator {

    private final Provider<FeedHolder> feedHolder;
    private final Provider<PipelineHolder> pipelineHolder;
    private final Provider<MetaHolder> metaHolder;
    private final Provider<SearchIdHolder> searchIdHolder;
    private final Provider<NodeInfo> nodeInfo;

    @Inject
    ExtendedPathCreator(final HomeDirProvider homeDirProvider,
                        final TempDirProvider tempDirProvider,
                        final Provider<FeedHolder> feedHolder,
                        final Provider<PipelineHolder> pipelineHolder,
                        final Provider<MetaHolder> metaHolder,
                        final Provider<SearchIdHolder> searchIdHolder,
                        final Provider<NodeInfo> nodeInfo) {
        super(homeDirProvider, tempDirProvider);
        this.feedHolder = feedHolder;
        this.pipelineHolder = pipelineHolder;
        this.metaHolder = metaHolder;
        this.searchIdHolder = searchIdHolder;
        this.nodeInfo = nodeInfo;
    }

    @Override
    public String replaceContextVars(String path) {
        if (feedHolder != null && feedHolder.get().getFeedName() != null) {
            path = replace(path, "feed", () -> feedHolder.get().getFeedName());
        }
        if (pipelineHolder != null && pipelineHolder.get().getPipeline() != null) {
            path = replace(path, "pipeline", () -> pipelineHolder.get().getPipeline().getName());
        }
        if (metaHolder != null && metaHolder.get().getMeta() != null) {
            path = replace(path, "sourceId", () -> metaHolder.get().getMeta().getId(), 0);

            // TODO : DEPRECATED ALIAS FOR SOURCE ID.
            path = replace(path, "streamId", () -> metaHolder.get().getMeta().getId(), 0);
        }
        if (metaHolder != null) {
            path = replace(path, "partNo", () -> String.valueOf(metaHolder.get().getPartNo()));

            // TODO : DEPRECATED ALIAS FOR PART NO.
            path = replace(path, "streamNo", () -> String.valueOf(metaHolder.get().getPartNo()));
        }
        if (searchIdHolder != null && searchIdHolder.get().getSearchId() != null) {
            path = replace(path, "searchId", () -> searchIdHolder.get().getSearchId());
        }
        if (nodeInfo != null) {
            path = replace(path, "node", () -> nodeInfo.get().getThisNodeName());
        }

        return path;
    }
}
