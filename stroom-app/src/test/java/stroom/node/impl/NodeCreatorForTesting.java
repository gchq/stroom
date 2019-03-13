/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.node.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.index.service.IndexVolumeService;
import stroom.index.shared.IndexVolume;
import stroom.pipeline.writer.PathCreator;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

class NodeCreatorForTesting implements NodeCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeCreatorForTesting.class);

    private final String NODE1 = "node1a";
    private final String NODE2 = "node2a";

    private final NodeConfig nodeConfig;
    private final IndexVolumeService volumeService;

    @Inject
    NodeCreatorForTesting(final NodeConfig nodeConfig,
                          final IndexVolumeService volumeService) {
        this.nodeConfig = nodeConfig;
        this.volumeService = volumeService;
    }

    private List<IndexVolume> getInitialVolumeList() {
        final List<IndexVolume> volumes = new ArrayList<>();
        volumes.add(createVolume("${stroom.temp}/rack1/node1a/v1", NODE1));
        volumes.add(createVolume("${stroom.temp}/rack1/node1a/v2", NODE1));
        volumes.add(createVolume("${stroom.temp}/rack2/node2a/v1", NODE2));
        volumes.add(createVolume("${stroom.temp}/rack2/node2a/v2", NODE2));
        return volumes;
    }

    private IndexVolume createVolume(final String path, final String nodeName) {
        final IndexVolume vol = new IndexVolume();
        final String p = PathCreator.replaceSystemProperties(path);
        vol.setPath(p);
        vol.setNodeName(nodeName);
        return vol;
    }

    @Override
    public void setup() {
        try {
            final List<IndexVolume> initialVolumeList = getInitialVolumeList();
            final List<IndexVolume> existingVolumes = volumeService.getAll();
            for (final IndexVolume volume : initialVolumeList) {
                boolean found = false;
                for (final IndexVolume existingVolume : existingVolumes) {
                    if (existingVolume.getNodeName().equals(volume.getNodeName())
                            && existingVolume.getPath().equals(volume.getPath())) {
                        found = true;
                    }

                }

                if (!found) {
                    Files.createDirectories(Paths.get(volume.getPath()));
                    volumeService.create(volume.getNodeName(), volume.getPath());
                }
            }

            nodeConfig.setNodeName(NODE1);
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
