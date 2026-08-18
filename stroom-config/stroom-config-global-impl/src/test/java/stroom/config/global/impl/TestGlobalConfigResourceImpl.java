/*
 * Copyright 2020 Crown Copyright
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
import stroom.config.global.shared.ConfigTarget;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.config.global.shared.SetConfigValueRequest;
import stroom.docref.DocRef;
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
import stroom.ui.config.shared.AbstractAnalyticUiDefaultConfig;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.ui.config.shared.ReportUiDefaultConfig;
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
        CONFIG_PROPERTY_1 = ConfigProperty.builder()
                .name(PropertyPath.fromPathString("a.property"))
                .yamlOverrideValue("a string")
                .build();

        CONFIG_PROPERTY_2 = ConfigProperty.builder()
                .name(PropertyPath.fromPathString("some.other.property"))
                .yamlOverrideValue("123")
                .build();

        CONFIG_PROPERTY_3 = ConfigProperty.builder()
                .name(PropertyPath.fromPathString("and.another.property"))
                .yamlOverrideValue("true")
                .build();
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

        final ListConfigResponse expectedResponse = new ListConfigResponse(
                List.of(
                        CONFIG_PROPERTY_1.copy().yamlOverrideValue("node1").build(),
                        CONFIG_PROPERTY_2.copy().yamlOverrideValue("node1").build(),
                        CONFIG_PROPERTY_3.copy().yamlOverrideValue("node1").build()),
                "node1a");

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

        final ConfigProperty configProperty = ConfigProperty.builder()
                .name(CONFIG_PROPERTY_2.getName())
                .yamlOverrideValue("node1")
                .build();

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

    /**
     * The value arrives as a {@link DocRef} so that the server, not the client, decides how it is stored. What
     * matters here is that the request is routed to the right config object and property name.
     */
    @Test
    void setConfigValue_docRef() {
        initNodes();

        final DocRef feed = DocRef.builder()
                .type("Feed")
                .uuid("87c3e7f2-27a5-4a63-9dcb-6b2a9e4e9d0f")
                .name("MY_ERROR_FEED")
                .build();

        doPostTest(
                GlobalConfigResource.SET_CONFIG_VALUE_SUB_PATH,
                SetConfigValueRequest.docRef(ConfigTarget.ANALYTIC_UI_DEFAULT,
                        AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_ERROR_FEED, feed),
                Boolean.class,
                true);

        verify(globalConfigServiceMap.get("node1"), times(1))
                .setDocRef(
                        Mockito.any(AnalyticUiDefaultConfig.class),
                        eq(AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_ERROR_FEED),
                        eq(feed));
    }

    /**
     * Reports have their own set of defaults, so the report properties must not be written to the analytic config.
     */
    @Test
    void setConfigValue_reportUsesReportConfig() {
        initNodes();

        final DocRef feed = DocRef.builder()
                .type("Feed")
                .uuid("1f0f4a2c-7f31-4d0e-9a8e-2b7cf0f5a111")
                .name("MY_DESTINATION_FEED")
                .build();

        doPostTest(
                GlobalConfigResource.SET_CONFIG_VALUE_SUB_PATH,
                SetConfigValueRequest.docRef(ConfigTarget.REPORT_UI_DEFAULT,
                        AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_DESTINATION_FEED, feed),
                Boolean.class,
                true);

        verify(globalConfigServiceMap.get("node1"), times(1))
                .setDocRef(
                        Mockito.any(ReportUiDefaultConfig.class),
                        eq(AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_DESTINATION_FEED),
                        eq(feed));
    }

    /**
     * The node is a plain string rather than a doc ref, so it takes the other branch.
     */
    @Test
    void setConfigValue_string() {
        initNodes();

        doPostTest(
                GlobalConfigResource.SET_CONFIG_VALUE_SUB_PATH,
                SetConfigValueRequest.string(ConfigTarget.ANALYTIC_UI_DEFAULT,
                        AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_NODE, "node1"),
                Boolean.class,
                true);

        verify(globalConfigServiceMap.get("node1"), times(1))
                .setString(
                        Mockito.any(AnalyticUiDefaultConfig.class),
                        eq(AbstractAnalyticUiDefaultConfig.PROP_NAME_DEFAULT_NODE),
                        eq("node1"));
    }

    @Test
    void getPropertyByName() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.PROPERTIES_SUB_PATH,
                "some.other.property");

        final ConfigProperty expectedResponse = CONFIG_PROPERTY_2.copy().yamlOverrideValue("node1").build();

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

        final ConfigProperty newConfigProperty = ConfigProperty.builder()
                .name(PropertyPath.fromPathString("a.new.config.prop"))
                .build();

        final ConfigProperty expectedConfigProperty = ConfigProperty.builder()
                .name(PropertyPath.fromPathString("a.new.config.prop"))
                .id(1)
                .version(1)
                .build();

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

        final ConfigProperty existingConfigProperty = ConfigProperty.builder()
                .name(PropertyPath.fromPathString("a.new.config.prop"))
                .id(1)
                .version(1)
                .build();

        final ConfigProperty expectedConfigProperty = ConfigProperty.builder()
                .name(PropertyPath.fromPathString("a.new.config.prop"))
                .id(1)
                .version(2)
                .build();

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
                                .map(configProperty ->
                                        configProperty.copy().yamlOverrideValue(node.getNodeName()).build())
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
                            .map(configProperty ->
                                    configProperty.copy().yamlOverrideValue(node.getNodeName()).build())
                            .filter(configProperty -> configProperty.getName().equals(propertyPath))
                            .findFirst();
                });

        when(globalConfigService.update((ConfigProperty) Mockito.any()))
                .thenAnswer(invocation -> {
                    final ConfigProperty configProperty = invocation.getArgument(0);
                    return configProperty.copy()
                            .id(1)
                            .version(configProperty.getVersion() == null
                                    ? 1
                                    : configProperty.getVersion() + 1)
                            .build();
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
                AnnotationState::new,
                AnalyticUiDefaultConfig::new,
                ReportUiDefaultConfig::new);
    }
}
