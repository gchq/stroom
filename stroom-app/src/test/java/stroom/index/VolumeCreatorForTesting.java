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

package stroom.index;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.IndexVolumeService;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.node.impl.NodeConfig;
import stroom.test.AppConfigTestModule;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class VolumeCreatorForTesting implements VolumeCreator {

    private static final Logger LOGGER = LoggerFactory.getLogger(VolumeCreatorForTesting.class);

    private static final String NODE1 = AppConfigTestModule.NODE1;
    private static final String NODE2 = "node2a";

    private final NodeConfig nodeConfig;
    private final IndexVolumeService volumeService;
    private final IndexVolumeGroupService indexVolumeGroupService;

    @Inject
    VolumeCreatorForTesting(final NodeConfig nodeConfig,
                            final IndexVolumeService volumeService,
                            final IndexVolumeGroupService indexVolumeGroupService) {
        this.nodeConfig = nodeConfig;
        this.volumeService = volumeService;
        this.indexVolumeGroupService = indexVolumeGroupService;
    }

    private List<IndexVolume> getInitialVolumeList(final Path tempDir) {
        final List<IndexVolume> volumes = new ArrayList<>();
        volumes.add(createVolume(tempDir, "rack1/node1a/v1", NODE1));
        volumes.add(createVolume(tempDir, "rack1/node1a/v2", NODE1));
        volumes.add(createVolume(tempDir, "rack2/node2a/v1", NODE2));
        volumes.add(createVolume(tempDir, "rack2/node2a/v2", NODE2));
        return volumes;
    }

    private IndexVolume createVolume(final Path tempDir, final String path, final String nodeName) {
        final IndexVolume vol = new IndexVolume();
        final String p = tempDir.resolve(path).toAbsolutePath().toString();
        vol.setPath(p);
        vol.setNodeName(nodeName);
        return vol;
    }

    @Override
    public void setup(final Path dir) {
        try {
            final IndexVolumeGroup indexVolumeGroup = indexVolumeGroupService.getOrCreate(DEFAULT_VOLUME_GROUP);
            final List<IndexVolume> initialVolumeList = getInitialVolumeList(dir);
            final List<IndexVolume> existingVolumes = volumeService.find(new ExpressionCriteria()).getValues();
            for (final IndexVolume volume : initialVolumeList) {
                boolean found = false;
                for (final IndexVolume existingVolume : existingVolumes) {
                    if (existingVolume.getNodeName().equals(volume.getNodeName())
                            && existingVolume.getPath().equals(volume.getPath())) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    Files.createDirectories(Paths.get(volume.getPath()));
                    final IndexVolume indexVolume = IndexVolume.builder()
                            .nodeName(volume.getNodeName())
                            .path(volume.getPath())
                            .indexVolumeGroupId(indexVolumeGroup.getId())
                            .build();
                    volumeService.create(indexVolume);
                }
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
