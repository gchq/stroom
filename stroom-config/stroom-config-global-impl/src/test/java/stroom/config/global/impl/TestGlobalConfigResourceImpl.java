package stroom.config.global.impl;

import stroom.config.common.UriFactory;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.GlobalConfigCriteria;
import stroom.config.global.shared.GlobalConfigResource;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.node.api.NodeService;
import stroom.node.mock.MockNodeService;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.ui.config.shared.UiConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.ResourcePaths;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.UriInfo;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

//@MockitoSettings(strictness = Strictness.LENIENT)
class TestGlobalConfigResourceImpl extends AbstractResourceTest<GlobalConfigResource> {

    private final Map<String, GlobalConfigService> globalConfigServiceMap = new HashMap<>();

    public static final ConfigProperty CONFIG_PROPERTY_1;
    public static final ConfigProperty CONFIG_PROPERTY_2;
    public static final ConfigProperty CONFIG_PROPERTY_3;

    @Mock
    private StroomEventLoggingService stroomEventLoggingService;
    @Mock
    private GlobalConfigService globalConfigService;
    //    @Mock
    private NodeService nodeService = new MockNodeService();
    @Mock
    private UriFactory uriFactory;

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
        final String subPath = GlobalConfigResource.PROPERTIES_SUB_PATH;

        final ListConfigResponse expectedResponse = FULL_PROP_LIST;

        when(globalConfigService.list(Mockito.any(GlobalConfigCriteria.class)))
                .thenAnswer(invocation -> {
                    System.out.println("list called");
                    try {
                        return FULL_PROP_LIST;
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                        throw e;
                    }
                });

        doPostTest(subPath,
                new GlobalConfigCriteria(),
                ListConfigResponse.class,
                expectedResponse);

        verify(globalConfigService, times(1))
                .list(eq(new GlobalConfigCriteria()));
    }

    @Test
    void list_partialName() {
        final String subPath = GlobalConfigResource.PROPERTIES_SUB_PATH;

        final ConfigProperty configProperty = new ConfigProperty(CONFIG_PROPERTY_2.getName());
        configProperty.setYamlOverrideValue("node1");

        final ListConfigResponse expectedResponse = new ListConfigResponse(List.of(
                configProperty
        ));

        final GlobalConfigCriteria criteria = new GlobalConfigCriteria("some");

        when(globalConfigService.list(Mockito.any(GlobalConfigCriteria.class)))
                .thenAnswer(invocation -> {
                    System.out.println("list called");
                    try {
                        return expectedResponse;
                    } catch (Exception e) {
                        e.printStackTrace(System.err);
                        throw e;
                    }
                });

        doPostTest(
                subPath,
                criteria,
                ListConfigResponse.class,
                expectedResponse);

        verify(globalConfigService, times(1))
                .list(eq(criteria));
    }

    @Test
    void listByNode() {
        final String nodeName = "node1";
        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.NODE_PROPERTIES_SUB_PATH,
                nodeName);

        final ListConfigResponse expectedResponse = FULL_PROP_LIST;

        final GlobalConfigCriteria criteria = new GlobalConfigCriteria();
        when(globalConfigService.list(Mockito.eq(criteria)))
                .thenReturn(expectedResponse);

        doPostTest(
                subPath,
                criteria,
                ListConfigResponse.class,
                expectedResponse);

//        verify().
//                Mockito.eq(nodeName),
//                Mockito.eq(ListConfigResponse.class),
//                Mockito.any(),
//                Mockito.any(),
//                Mockito.any());
    }

    @Test
    void getPropertyByName() {
        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.PROPERTIES_SUB_PATH,
                "some.other.property");

        final ConfigProperty expectedResponse = CONFIG_PROPERTY_2;

        when(globalConfigService.fetch(Mockito.any()))
                .thenAnswer(invocation -> {
                    PropertyPath propertyPath = invocation.getArgument(0);
                    return FULL_PROP_LIST.stream()
                            .filter(configProperty -> configProperty.getName().equals(propertyPath))
                            .findFirst();
                });

        final ConfigProperty listConfigResponse = doGetTest(
                subPath,
                ConfigProperty.class,
                expectedResponse);
    }

    @Test
    void getYamlValueByName() {
        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                "some.other.property",
                GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH);

        final OverrideValue<String> expectedResponse = OverrideValue.with("node1");

        when(globalConfigService.fetch(Mockito.any()))
                .thenAnswer(invocation -> {
                    PropertyPath propertyPath = invocation.getArgument(0);
                    return FULL_PROP_LIST.stream()
                            .filter(configProperty -> configProperty.getName().equals(propertyPath))
                            .peek(configProperty -> {
                                configProperty.setYamlOverrideValue(expectedResponse.getValue());
                            })
                            .findFirst();
                });

        final OverrideValue<String> listConfigResponse = doGetTest(
                subPath,
                OverrideValue.class,
                expectedResponse);
    }


    @Test
    void getYamlValueByNodeAndName() {
        final String propName = "some.other.property";
        final String nodeName = "node1";
        final String subPath = ResourcePaths.buildPath(
                GlobalConfigResource.CLUSTER_PROPERTIES_SUB_PATH,
                propName,
                GlobalConfigResource.YAML_OVERRIDE_VALUE_SUB_PATH,
                nodeName);

        final OverrideValue<String> expectedResponse = OverrideValue.with(nodeName);

        when(globalConfigService.fetch(Mockito.any()))
                .thenReturn(FULL_PROP_LIST.stream()
                        .filter(configProperty ->
                                configProperty.getName().toString().equals(propName))
                        .peek(configProperty ->
                                configProperty.setYamlOverrideValue(expectedResponse.getValue()))
                        .findFirst());

        final OverrideValue<String> listConfigResponse = doGetTest(
                subPath,
                OverrideValue.class,
                expectedResponse);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void getYamlValueByNodeAndName_otherNode() {
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

//        Assertions.assertThat(getRequestEvents("node1"))
//                .hasSize(1);
//        Assertions.assertThat(getRequestEvents("node2"))
//                .hasSize(1);
//        Assertions.assertThat(getRequestEvents("node3"))
//                .hasSize(0);
    }

    @Test
    void create() {

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
    public GlobalConfigResource getRestResource() {

        return new GlobalConfigResourceImpl(
                MockStroomEventLoggingService::new,
                () -> globalConfigService,
                () -> nodeService,
                UiConfig::new,
                () -> uriFactory,
                // TODO @AT fix me
                new RequestInfoHolder() {
                    @Override
                    public UriInfo getUriInfo() {
                        return null;
                    }
                });
    }

//    @Override
//    public GlobalConfigResource getRestResource() {
//    public GlobalConfigResource getRestResource(final TestNode node,
//                                                final List<TestNode> allNodes,
//                                                final Map<String, String> baseEndPointUrls) {

    // Set up the GlobalConfigResource mock


//        when(globalConfigService.list(Mockito.any(GlobalConfigCriteria.class)))
//                .thenAnswer(invocation -> {
//                    System.out.println("list called");
//                    try {
//                        GlobalConfigCriteria criteria = invocation.getArgument(0);
//                        Predicate<ConfigProperty> predicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
//                                criteria.getQuickFilterInput(), fieldMappers);
//
//                        return new ListConfigResponse(FULL_PROP_LIST.stream()
//                                .peek(configProperty -> {
//                                    configProperty.setYamlOverrideValue(node.getNodeName());
//                                })
//                                .filter(predicate)
//                                .collect(Collectors.toList()));
//                    } catch (Exception e) {
//                        e.printStackTrace(System.err);
//                        throw e;
//                    }
//                });
//
//        when(globalConfigService.fetch(Mockito.any()))
//                .thenAnswer(invocation -> {
//                    PropertyPath propertyPath = invocation.getArgument(0);
//                    return FULL_PROP_LIST.stream()
//                            .peek(configProperty -> {
//                                configProperty.setYamlOverrideValue(node.getNodeName());
//                            })
//                            .filter(configProperty -> configProperty.getName().equals(propertyPath))
//                            .findFirst();
//                });
//
//        when(globalConfigService.update(Mockito.any()))
//                .thenAnswer(invocation -> {
//                    ConfigProperty configProperty = invocation.getArgument(0);
//                    configProperty.setId(1);
//                    configProperty.setVersion(configProperty.getVersion() == null
//                            ? 1
//                            : configProperty.getVersion() + 1);
//                    return configProperty;
//                });
//
//        globalConfigServiceMap.put(node.getNodeName(), globalConfigService);
//
//        // Set up the NodeService mock
//        final NodeService nodeService = createNamedMock(NodeService.class, node);
//
//        when(nodeService.isEnabled(Mockito.anyString()))
//                .thenAnswer(invocation ->
//                        allNodes.stream()
//                                .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
//                                .anyMatch(TestNode::isEnabled));
//
//        when(nodeService.getBaseEndpointUrl(Mockito.anyString()))
//                .thenAnswer(invocation ->
//                        baseEndPointUrls.get(invocation.getArgument(0)));
//
////        when(nodeService.remoteRestResult(
////                Mockito.anyString(),
////                Mockito.anyString(),
////                Mockito.any(),
////                Mockito.any(),
////                Mockito.any())).thenCallRealMethod();
////
////        when(nodeService.remoteRestResult(
////                Mockito.anyString(),
////                Mockito.any(Class.class),
////                Mockito.any(),
////                Mockito.any(),
////                Mockito.any())).thenCallRealMethod();
//
//        // Set up the NodeInfo mock
//
//        final NodeInfo nodeInfo = createNamedMock(NodeInfo.class, node);
//
//        when(nodeInfo.getThisNodeName())
//                .thenReturn(node.getNodeName());

//        return new GlobalConfigResourceImpl(
//                () -> stroomEventLoggingService,
//                () -> globalConfigService,
//                () -> nodeService,
//                UiConfig::new,
//                null);
//    }
}
