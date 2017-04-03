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

import stroom.entity.shared.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.EntityData;
import stroom.explorer.shared.ExplorerData;
import stroom.pipeline.shared.FindPipelineEntityCriteria;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.PipelineEntityService;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ProvidesExplorerData
@Component
public class PipelineExplorerDataProvider
        extends AbstractExplorerDataProvider<PipelineEntity, FindPipelineEntityCriteria> {
    private final PipelineEntityService pipelineEntityService;

    @Inject
    PipelineExplorerDataProvider(@Named("cachedFolderService") final FolderService cachedFolderService, final PipelineEntityService pipelineEntityService) {
        super(cachedFolderService);
        this.pipelineEntityService = pipelineEntityService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        addItems(pipelineEntityService, treeModel);
    }

    @Override
    protected EntityData createEntityData(final PipelineEntity entity) {
        final EntityData entityData = super.createEntityData(entity);

        if (entity.getPipelineType() != null) {
            final Set<String> tags = new HashSet<>();
            tags.add(entity.getPipelineType());
            entityData.setTags(tags);
        }

        return entityData;
    }

    @Override
    public String getType() {
        return PipelineEntity.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return PipelineEntity.ENTITY_TYPE;
    }

    @Override
    public int getPriority() {
        return 6;
    }
}
