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

package stroom.config.global.impl;

import stroom.annotation.impl.AnnotationState;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.explorer.impl.ExplorerConfig;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.impl.StroomReceiptPolicyConfig;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.StroomOpenIdConfig;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestGlobalConfigResourceImpl extends AbstractMultiNodeResourceTest<GlobalConfigResource> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestGlobalConfigResourceImpl.class);

    private final Map<String, GlobalConfigService> globalConfigServiceMap = new HashMap<>();

    public static final ConfigProperty CONFIG_PROPERTY_1;
    public static final ConfigProperty CONFIG_PROPERTY_2;
    public static final ConfigProperty CONFIG_PROPERTY_3;

    static {
        ConfigProperty configProperty = new ConfigProperty(PropertyPath.fromPathString("a.property"));
        configProperty.setYamlOverrideValue("a string");
        CONFIG_PROPERTY_1 = configProperty;

        configProperty = new ConfigProperty(PropertyPath.fromPathString("some.other.property"));
        configProperty.setYamlOverrideValue("123");
        CONFIG_PROPERTY_2 = configProperty;

        configProperty = new ConfigProperty(PropertyPath.fromPathString("and.another.property"));
        configProperty.setYamlOverrideValue("true");
        CONFIG_PROPERTY_3 = configProperty;
    }

    private static final ListConfigResponse FULL_PROP_LIST = new ListConfigResponse(
            List.of(CONFIG_PROPERTY_1, CONFIG_PROPERTY_2, CONFIG_PROPERTY_3),
            "node1a");

    private static final int BASE_PORT = 7000;

    public TestGlobalConfigResourceImpl() {
        super(createNodeList(BASE_PORT));
    }

    @Test
    void list() {
        initNodes();

        final String subPath = GlobalConfigResource.PROPERTIES_SUB_PATH;

        final ListConfigResponse expectedResponse = FULL_PROP_LIST;

        doPostTest(subPath,
                new GlobalConfigCriteria(),
                ListConfigResponse.class,
                expectedResponse);

        verify(globalConfigServiceMap.get("node1"), times(1))
                .list(eq(new GlobalConfigCriteria()));
    }

    @Test
    void list_partialName() {
        initNodes();

        final String subPath = GlobalConfigResource.PROPERTIES_SUB_PATH;

        final ConfigProperty configProperty = new ConfigProperty(CONFIG_PROPERTY_2.getName());
        configProperty.setYamlOverrideValue("node1");

        final ListConfigResponse expectedResponse = new ListConfigResponse(List.of(
                configProperty
        ), "node1a");

        final GlobalConfigCriteria criteria = new GlobalConfigCriteria("some");

        doPostTest(subPath, criteria, ListConfigResponse.class, expectedResponse);

        verify(globalConfigServiceMap.get("node1"), times(1))
                .list(eq(criteria));
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void listByNode_thisNode() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                "node1");

        final ListConfigResponse expectedResponse = FULL_PROP_LIST;

        doPostTest(
                subPath,
                new GlobalConfigCriteria(),
                ListConfigResponse.class,
                expectedResponse);

        verify(globalConfigServiceMap.get("node1"), times(1))
                .list(new GlobalConfigCriteria());

        Assertions.assertThat(getRequestEvents("node1"))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents("node2"))
                .hasSize(0);
        Assertions.assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void listByNode_otherNode() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                "node2");

        final ListConfigResponse expectedResponse = FULL_PROP_LIST;

        doPostTest(
                subPath,
                new GlobalConfigCriteria(),
                ListConfigResponse.class,
                expectedResponse);

        verify(globalConfigServiceMap.get("node2"), times(1))
                .list(eq(new GlobalConfigCriteria()));

        Assertions.assertThat(getRequestEvents("node1"))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents("node2"))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Test
    void getPropertyByName() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.PROPERTIES_SUB_PATH,
                "some.other.property");

        final ConfigProperty expectedResponse = CONFIG_PROPERTY_2;

        final ConfigProperty listConfigResponse = doGetTest(
                subPath,
                ConfigProperty.class,
                expectedResponse);
    }


    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void getYamlValueByNodeAndName_sameNode() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                "some.other.property",
                GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                "node1");

        final OverrideValue<String> expectedResponse = OverrideValue.with("node1");

        final OverrideValue<String> listConfigResponse = doGetTest(
                subPath,
                OverrideValue.class,
                expectedResponse);

        Assertions.assertThat(getRequestEvents("node1"))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents("node2"))
                .hasSize(0);
        Assertions.assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void getYamlValueByNodeAndName_otherNode() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                "some.other.property",
                GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                "node2");

        final OverrideValue<String> expectedResponse = OverrideValue.with("node2");

        final OverrideValue<String> listConfigResponse = doGetTest(
                subPath,
                OverrideValue.class,
                expectedResponse);

        Assertions.assertThat(getRequestEvents("node1"))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents("node2"))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Test
    void create() {

        initNodes();

        final String subPath = "";

        final ConfigProperty newConfigProperty = new ConfigProperty(
                PropertyPath.fromPathString("a.new.config.prop"));

        final ConfigProperty expectedConfigProperty = new ConfigProperty(
                PropertyPath.fromPathString("a.new.config.prop"));
        expectedConfigProperty.setId(1);
        expectedConfigProperty.setVersion(1);

        final ConfigProperty createdConfigProperty = doPostTest(
                subPath,
                newConfigProperty,
                ConfigProperty.class,
                expectedConfigProperty);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void update() {

        initNodes();

        final ConfigProperty existingConfigProperty = new ConfigProperty(
                PropertyPath.fromPathString("a.new.config.prop"));
        existingConfigProperty.setId(1);
        existingConfigProperty.setVersion(1);

        final ConfigProperty expectedConfigProperty = new ConfigProperty(
                PropertyPath.fromPathString("a.new.config.prop"));
        expectedConfigProperty.setId(1);
        expectedConfigProperty.setVersion(2);

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                existingConfigProperty.getNameAsString(),
                GlobalConfigResource.DB_OVERRIDE_VALUE_SUB_PATH);

        final ConfigProperty createdConfigProperty = doPutTest(
                subPath,
                existingConfigProperty,
                ConfigProperty.class,
                expectedConfigProperty);
    }

    @Test
    void fetchExtendedUiConfig() {
        initNodes();

        final String subPath = GlobalConfigResource.FETCH_EXTENDED_UI_CONFIG_SUB_PATH;
        final ExtendedUiConfig expectedResponse = new ExtendedUiConfig();

        final ExtendedUiConfig response = doGetTest(
                subPath,
                ExtendedUiConfig.class,
                expectedResponse);

    }

    @Override
    public String getResourceBasePath() {
        return GlobalConfigResource.BASE_PATH;
    }

    @Override
    public GlobalConfigResource getRestResource(final TestNode node,
                                                final List<TestNode> allNodes,
                                                final Map<String, String> baseEndPointUrls) {

        LOGGER.info("Setting up mocked node {}", node);
        // Set up the GlobalConfigResource mock
        final GlobalConfigService globalConfigService = createNamedMock(GlobalConfigService.class, node);
        final StroomEventLoggingService stroomEventLoggingService = new MockStroomEventLoggingService();

        when(globalConfigService.list(Mockito.any(GlobalConfigCriteria.class)))
                .thenAnswer(invocation -> {
                    System.out.println("list called");
                    try {
                        final GlobalConfigCriteria criteria = invocation.getArgument(0);
                        final ExpressionPredicateFactory expressionPredicateFactory = new ExpressionPredicateFactory();

                        Stream<ConfigProperty> stream = FULL_PROP_LIST.stream();
                        stream = expressionPredicateFactory.filterAndSortStream(
                                stream,
                                criteria.getQuickFilterInput(),
                                GlobalConfigService.FIELD_PROVIDER,
                                GlobalConfigService.VALUE_FUNCTION_FACTORIES,
                                Optional.empty());
                        final List<ConfigProperty> list = stream
                                .peek(configProperty -> configProperty.setYamlOverrideValue(node.getNodeName()))
                                .toList();

                        return new ListConfigResponse(list, "node1a");
                    } catch (final Exception e) {
                        e.printStackTrace(System.err);
                        throw e;
                    }
                });

        when(globalConfigService.fetch(Mockito.any()))
                .thenAnswer(invocation -> {
                    final PropertyPath propertyPath = invocation.getArgument(0);
                    return FULL_PROP_LIST.stream()
                            .peek(configProperty -> {
                                configProperty.setYamlOverrideValue(node.getNodeName());
                            })
                            .filter(configProperty -> configProperty.getName().equals(propertyPath))
                            .findFirst();
                });

        when(globalConfigService.update(Mockito.any()))
                .thenAnswer(invocation -> {
                    final ConfigProperty configProperty = invocation.getArgument(0);
                    configProperty.setId(1);
                    configProperty.setVersion(configProperty.getVersion() == null
                            ? 1
                            : configProperty.getVersion() + 1);
                    return configProperty;
                });

        globalConfigServiceMap.put(node.getNodeName(), globalConfigService);

        // Set up the NodeService mock
        final NodeService nodeService = createNamedMock(NodeService.class, node);

        when(nodeService.isEnabled(Mockito.anyString()))
                .thenAnswer(invocation ->
                        allNodes.stream()
                                .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
                                .anyMatch(TestNode::isEnabled));

        when(nodeService.getBaseEndpointUrl(Mockito.anyString()))
                .thenAnswer(invocation ->
                        baseEndPointUrls.get(invocation.getArgument(0)));

//        when(nodeService.remoteRestResult(
//                Mockito.anyString(),
//                Mockito.anyString(),
//                Mockito.any(),
//                Mockito.any(),
//                Mockito.any())).thenCallRealMethod();
//
//        when(nodeService.remoteRestResult(
//                Mockito.anyString(),
//                Mockito.any(Class.class),
//                Mockito.any(),
//                Mockito.any(),
//                Mockito.any())).thenCallRealMethod();

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = createNamedMock(NodeInfo.class, node);

        when(nodeInfo.getThisNodeName())
                .thenReturn(node.getNodeName());

        return new GlobalConfigResourceImpl(
                () -> stroomEventLoggingService,
                () -> globalConfigService,
                () -> nodeService,
                UiConfig::new,
                () -> nodeInfo,
                StroomOpenIdConfig::new,
                ExplorerConfig::new,
                AuthenticationConfig::new,
                StroomReceiptPolicyConfig::new,
                ReceiveDataConfig::new,
                AnnotationState::new);
    }
}
