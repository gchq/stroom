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

package stroom.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.StroomEntityManager;
import stroom.entity.util.BaseEntityUtil;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.VolumeEntity;
import stroom.node.shared.VolumeState;
import stroom.util.config.StroomProperties;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class NodeConfigForTesting implements NodeConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeConfigForTesting.class);

    private final Rack rack1 = createRack("rack1");
    private final Rack rack2 = createRack("rack2");
    private final Node node1a = createNode("node1a", rack1);
    private final Node node2a = createNode("node2a", rack2);

    private final NodeServiceImpl nodeService;
    private final VolumeService volumeService;
    private final StroomEntityManager stroomEntityManager;

    @Inject
    public NodeConfigForTesting(final NodeServiceImpl nodeService,
                                final VolumeService volumeService,
                                final StroomEntityManager stroomEntityManager) {
        this.nodeService = nodeService;
        this.volumeService = volumeService;
        this.stroomEntityManager = stroomEntityManager;
    }

    private List<Rack> getInitialRackList() {
        final List<Rack> racks = new ArrayList<>();
        racks.add(rack1);
        racks.add(rack2);
        return racks;
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

    private Rack createRack(final String name) {
        final Rack rack = new Rack();
        rack.setName(name);
        return rack;
    }

    private Node createNode(final String name, final Rack rack) {
        final Node node = new Node();
        node.setName(name);
        node.setRack(rack);
        return node;
    }

    private VolumeEntity createVolume(final String path, final Node node) {
        final VolumeEntity vol = new VolumeEntity();
        final String p = StroomProperties.replaceProperties(path);
        vol.setPath(p);
        vol.setNode(node);
        return vol;
    }

    @Override
    public void setup() {
        try {
            final List<Rack> initialRackList = getInitialRackList();
            final List<Node> initialNodeList = getInitialNodeList();
            final List<VolumeEntity> initialVolumeList = getInitialVolumeList();

            final List<Rack> realRackList = new ArrayList<>();
            final List<Node> realNodeList = new ArrayList<>();

            for (final Rack rack : initialRackList) {
                final Rack realRack = nodeService.getRack(rack.getName());
                if (realRack != null) {
                    realRackList.add(realRack);
                } else {
                    realRackList.add(stroomEntityManager.saveEntity(rack.copy()));
                }
            }
            for (final Node node : initialNodeList) {
                Node realNode = nodeService.getNode(node.getName());
                if (realNode == null) {
                    realNode = node.copy();
                    realNode.setRack(BaseEntityUtil.findByName(realRackList, realNode.getRack().getName()));
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

            nodeService.setNodeName(initialNodeList.get(0).getName());
            nodeService.setRackName(initialRackList.get(0).getName());
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
