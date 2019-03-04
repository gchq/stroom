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
import stroom.entity.StroomEntityManager;
import stroom.index.service.IndexVolumeService;
import stroom.index.shared.IndexVolume;
import stroom.node.shared.Node;
import stroom.pipeline.writer.PathCreator;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class NodeCreatorForTesting implements NodeCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeCreatorForTesting.class);

    private final Node node1a = createNode("node1a");
    private final Node node2a = createNode("node2a");

    private final NodeServiceImpl nodeService;
    private final NodeConfig nodeConfig;
    private final IndexVolumeService volumeService;
    private final StroomEntityManager stroomEntityManager;

    @Inject
    public NodeCreatorForTesting(final NodeServiceImpl nodeService,
                                 final NodeConfig nodeConfig,
                                 final IndexVolumeService volumeService,
                                 final StroomEntityManager stroomEntityManager) {
        this.nodeService = nodeService;
        this.nodeConfig = nodeConfig;
        this.volumeService = volumeService;
        this.stroomEntityManager = stroomEntityManager;
    }

    private List<Node> getInitialNodeList() {
        final List<Node> nodes = new ArrayList<>();
        nodes.add(node1a);
        nodes.add(node2a);
        return nodes;
    }

    private List<IndexVolume> getInitialVolumeList() {
        final List<IndexVolume> volumes = new ArrayList<>();
        volumes.add(createVolume("${stroom.temp}/rack1/node1a/v1", node1a));
        volumes.add(createVolume("${stroom.temp}/rack1/node1a/v2", node1a));
        volumes.add(createVolume("${stroom.temp}/rack2/node2a/v1", node2a));
        volumes.add(createVolume("${stroom.temp}/rack2/node2a/v2", node2a));
        return volumes;
    }

    private Node createNode(final String name) {
        final Node node = new Node();
        node.setName(name);
        return node;
    }

    private IndexVolume createVolume(final String path, final Node node) {
        final IndexVolume vol = new IndexVolume();
        final String p = PathCreator.replaceSystemProperties(path);
        vol.setPath(p);
        vol.setNodeName(node.getName());
        return vol;
    }

    @Override
    public void setup() {
        try {
            final List<Node> initialNodeList = getInitialNodeList();
            final List<IndexVolume> initialVolumeList = getInitialVolumeList();

            final List<Node> realNodeList = new ArrayList<>();

            for (final Node node : initialNodeList) {
                Node realNode = nodeService.getNode(node.getName());
                if (realNode == null) {
                    realNode = node.copy();
                    LOGGER.debug("Persisting node {}", realNode);
                    realNode = stroomEntityManager.saveEntity(realNode);
                }
                realNodeList.add(realNode);
            }

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

            nodeConfig.setNodeName(initialNodeList.get(0).getName());
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
