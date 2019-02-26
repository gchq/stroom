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
import stroom.entity.util.BaseEntityUtil;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeState;
import stroom.pipeline.writer.PathCreator;
import stroom.volume.VolumeService;

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
    private final VolumeService volumeService;
    private final StroomEntityManager stroomEntityManager;

    @Inject
    public NodeCreatorForTesting(final NodeServiceImpl nodeService,
                                 final NodeConfig nodeConfig,
                                 final VolumeService volumeService,
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

    private List<VolumeEntity> getInitialVolumeList() {
        final List<VolumeEntity> volumes = new ArrayList<>();
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

    private VolumeEntity createVolume(final String path, final Node node) {
        final VolumeEntity vol = new VolumeEntity();
        final String p = PathCreator.replaceSystemProperties(path);
        vol.setPath(p);
        vol.setNode(node);
        return vol;
    }

    @Override
    public void setup() {
        try {
            final List<Node> initialNodeList = getInitialNodeList();
            final List<VolumeEntity> initialVolumeList = getInitialVolumeList();

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

            final List<VolumeEntity> existingVolumes = volumeService.find(new FindVolumeCriteria());
            for (final VolumeEntity volume : initialVolumeList) {
                boolean found = false;
                for (final VolumeEntity existingVolume : existingVolumes) {
                    if (existingVolume.getNode().getName().equals(volume.getNode().getName())
                            && existingVolume.getPath().equals(volume.getPath())) {
                        found = true;
                    }

                }

                if (!found) {
                    Files.createDirectories(Paths.get(volume.getPath()));

                    final Node node = BaseEntityUtil.findByName(realNodeList, volume.getNode().getName());

                    VolumeState volumeState = new VolumeState();
                    volumeState = stroomEntityManager.saveEntity(volumeState);

                    final VolumeEntity realVolume = volume.copy();
                    realVolume.setNode(node);
                    realVolume.setVolumeState(volumeState);

                    stroomEntityManager.saveEntity(realVolume);
                }
            }

            nodeConfig.setNodeName(initialNodeList.get(0).getName());
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
