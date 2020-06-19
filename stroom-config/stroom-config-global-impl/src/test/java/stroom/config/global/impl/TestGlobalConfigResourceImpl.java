package stroom.config.global.impl;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UiPreferences;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestGlobalConfigResourceImpl extends AbstractMultiNodeResourceTest<GlobalConfigResource> {

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

    private static final ListConfigResponse FULL_PROP_LIST = new ListConfigResponse(List.of(
            CONFIG_PROPERTY_1,
            CONFIG_PROPERTY_2,
            CONFIG_PROPERTY_3
    ));

    @Test
    void list() {
        initNodes();

        final String subPath = GlobalConfigResource.PROPERTIES_SUB_PATH;

        final ListConfigResponse expectedResponse = FULL_PROP_LIST;

        doGetTest(
                subPath,
                ListConfigResponse.class,
                expectedResponse,
                webTarget -> webTarget.queryParam("partialName", (String) null),
                webTarget -> webTarget.queryParam("offset", 0),
                webTarget -> webTarget.queryParam("size", 100));

        verify(globalConfigServiceMap.get("node1"), times(1))
                .list(Mockito.any(), eq(new PageRequest(0L, 100)));
    }

    @Test
    void list_partialName() {
        initNodes();

        final String subPath = GlobalConfigResource.PROPERTIES_SUB_PATH;

        final ConfigProperty configProperty = new ConfigProperty(CONFIG_PROPERTY_2.getName());
        configProperty.setYamlOverrideValue("node1");

        final ListConfigResponse expectedResponse = new ListConfigResponse(List.of(
                configProperty
        ));

        doGetTest(
                subPath,
                ListConfigResponse.class,
                expectedResponse,
                webTarget -> webTarget.queryParam("partialName", "some"),
                webTarget -> webTarget.queryParam("offset", 0),
                webTarget -> webTarget.queryParam("size", 100));

        verify(globalConfigServiceMap.get("node1"), times(1))
                .list(Mockito.any(), eq(new PageRequest(0L, 100)));
    }

    @Test
    void listByNode_thisNode() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                "node1");

        final ListConfigResponse expectedResponse = FULL_PROP_LIST;

        doGetTest(
                subPath,
                ListConfigResponse.class,
                expectedResponse,
                webTarget -> webTarget.queryParam("partialName", (String) null),
                webTarget -> webTarget.queryParam("offset", 0),
                webTarget -> webTarget.queryParam("size", 100));

        verify(globalConfigServiceMap.get("node1"), times(1))
                .list(Mockito.any(), eq(new PageRequest(0L, 100)));

        Assertions.assertThat(getRequestEvents("node1"))
                .hasSize(1);
        Assertions.assertThat(getRequestEvents("node2"))
                .hasSize(0);
        Assertions.assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Test
    void listByNode_otherNode() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                "node2");

        final ListConfigResponse expectedResponse = FULL_PROP_LIST;

        doGetTest(
                subPath,
                ListConfigResponse.class,
                expectedResponse,
                webTarget -> webTarget.queryParam("partialName", (String) null),
                webTarget -> webTarget.queryParam("offset", 0),
                webTarget -> webTarget.queryParam("size", 100));

        verify(globalConfigServiceMap.get("node2"), times(1))
                .list(Mockito.any(), eq(new PageRequest(0L, 100)));

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

        // Set up the GlobalConfigResource mock
        final GlobalConfigService globalConfigService = createNamedMock(GlobalConfigService.class, node);

        when(globalConfigService.list(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> {
                    Predicate<ConfigProperty> predicate = invocation.getArgument(0);

                    return new ListConfigResponse(FULL_PROP_LIST.stream()
                            .peek(configProperty -> {
                                configProperty.setYamlOverrideValue(node.getNodeName());
                            })
                            .filter(predicate)
                            .collect(Collectors.toList()));
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
                    configProperty.setVersion(configProperty.getVersion() == null ? 1 : configProperty.getVersion() + 1);
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
                .thenAnswer(invocation -> baseEndPointUrls.get((String) invocation.getArgument(0)));

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = createNamedMock(NodeInfo.class, node);

        when(nodeInfo.getThisNodeName())
                .thenReturn(node.getNodeName());

        return new GlobalConfigResourceImpl(
                globalConfigService,
                nodeService,
                new UiConfig(),
                nodeInfo,
                webTargetFactory(),
                null);
    }
}