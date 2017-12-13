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

package stroom.node.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.util.BaseEntityUtil;
import stroom.entity.server.util.StroomEntityManager;
import stroom.node.shared.FindVolumeCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.Rack;
import stroom.node.shared.Volume;
import stroom.node.shared.VolumeState;
import stroom.util.config.StroomProperties;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile(StroomSpringProfiles.IT)
public class NodeConfigForTesting implements NodeConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeConfigForTesting.class);

    private final Rack rack1 = createRack("rack1");
    private final Rack rack2 = createRack("rack2");
    private final Node node1a = createNode("node1a", rack1);
    private final Node node2a = createNode("node2a", rack2);

    private final NodeService nodeService;
    private final VolumeService volumeService;
    private final StroomEntityManager stroomEntityManager;

    @Inject
    public NodeConfigForTesting(final NodeService nodeService, final VolumeService volumeService,
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

    private List<Volume> getInitialVolumeList() {
        final List<Volume> volumes = new ArrayList<>();
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

    private Volume createVolume(final String path, final Node node) {
        final Volume vol = new Volume();
        final String p = StroomProperties.replaceProperties(path);
        vol.setPath(p);
        vol.setNode(node);
        return vol;
    }

    @Override
    public void setup() {
        try {
            final Object o = ((Advised) nodeService).getTargetSource().getTarget();
            final NodeServiceImpl nodeServiceImpl = (NodeServiceImpl) o;

            final List<Rack> initialRackList = getInitialRackList();
            final List<Node> initialNodeList = getInitialNodeList();
            final List<Volume> initialVolumeList = getInitialVolumeList();

            final List<Rack> realRackList = new ArrayList<>();
            final List<Node> realNodeList = new ArrayList<>();

            for (final Rack rack : initialRackList) {
                final Rack realRack = nodeServiceImpl.getRack(rack.getName());
                if (realRack != null) {
                    realRackList.add(realRack);
                } else {
                    realRackList.add(stroomEntityManager.saveEntity(BaseEntityUtil.clone(rack)));
                }
            }
            for (final Node node : initialNodeList) {
                Node realNode = nodeServiceImpl.getNode(node.getName());
                if (realNode == null) {
                    realNode = BaseEntityUtil.clone(node);
                    realNode.setRack(BaseEntityUtil.findByName(realRackList, realNode.getRack().getName()));
                    LOGGER.debug("Persisting node {}", realNode);
                    realNode = stroomEntityManager.saveEntity(realNode);
                }
                realNodeList.add(realNode);
            }

            final List<Volume> existingVolumes = volumeService.find(new FindVolumeCriteria());
            for (final Volume volume : initialVolumeList) {
                boolean found = false;
                for (final Volume existingVolume : existingVolumes) {
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

                    final Volume realVolume = BaseEntityUtil.clone(volume);
                    realVolume.setNode(node);
                    realVolume.setVolumeState(volumeState);

                    stroomEntityManager.saveEntity(realVolume);
                }
            }

            nodeServiceImpl.setNodeName(initialNodeList.get(0).getName());
            nodeServiceImpl.setRackName(initialRackList.get(0).getName());
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
