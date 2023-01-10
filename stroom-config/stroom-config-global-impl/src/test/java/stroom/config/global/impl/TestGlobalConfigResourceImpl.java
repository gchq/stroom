package stroom.config.global.impl;

import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.impl.StroomOpenIdConfig;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.ui.config.shared.UiConfig;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
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
import java.util.stream.Collectors;

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
            "node1a",
            "");

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
        ), "node1a", "");

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

        ConfigProperty newConfigProperty = new ConfigProperty(PropertyPath.fromPathString("a.new.config.prop"));

        ConfigProperty expectedConfigProperty = new ConfigProperty(PropertyPath.fromPathString("a.new.config.prop"));
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

        ConfigProperty existingConfigProperty = new ConfigProperty(PropertyPath.fromPathString("a.new.config.prop"));
        existingConfigProperty.setId(1);
        existingConfigProperty.setVersion(1);

        ConfigProperty expectedConfigProperty = new ConfigProperty(PropertyPath.fromPathString("a.new.config.prop"));
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
    void fetchUiConfig() {
        initNodes();

        String subPath = GlobalConfigResource.FETCH_UI_CONFIG_SUB_PATH;
        UiConfig expectedResponse = new UiConfig();

        final UiConfig listConfigResponse = doGetTest(
                subPath,
                UiConfig.class,
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

        final FilterFieldMappers<ConfigProperty> fieldMappers = FilterFieldMappers.of(
                FilterFieldMapper.of(GlobalConfigResource.FIELD_DEF_NAME, ConfigProperty::getNameAsString)
        );

        when(globalConfigService.list(Mockito.any(GlobalConfigCriteria.class)))
                .thenAnswer(invocation -> {
                    System.out.println("list called");
                    try {
                        GlobalConfigCriteria criteria = invocation.getArgument(0);

                        final ListConfigResponse response = new ListConfigResponse(
                                QuickFilterPredicateFactory.filterStream(
                                        criteria.getQuickFilterInput(),
                                        fieldMappers,
                                        FULL_PROP_LIST.stream())
                                        .peek(configProperty ->
                                                configProperty.setYamlOverrideValue(node.getNodeName()))
                                        .collect(Collectors.toList()),
                                "node1a",
                                QuickFilterPredicateFactory.fullyQualifyInput(
                                        criteria.getQuickFilterInput(),
                                        fieldMappers));
                        return response;
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                        throw e;
                    }
                });

        when(globalConfigService.fetch(Mockito.any()))
                .thenAnswer(invocation -> {
                    PropertyPath propertyPath = invocation.getArgument(0);
                    return FULL_PROP_LIST.stream()
                            .peek(configProperty -> {
                                configProperty.setYamlOverrideValue(node.getNodeName());
                            })
                            .filter(configProperty -> configProperty.getName().equals(propertyPath))
                            .findFirst();
                });

        when(globalConfigService.update(Mockito.any()))
                .thenAnswer(invocation -> {
                    ConfigProperty configProperty = invocation.getArgument(0);
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
                null,
                () -> nodeInfo,
                StroomOpenIdConfig::new);
    }
}
