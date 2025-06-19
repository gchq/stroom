package stroom.pipeline.xsltfunctions;

import stroom.data.store.api.AttributeMapFactory;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.pipeline.state.MetaHolder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaStream extends AbstractXsltFunctionTest<MetaStream> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestMetaStream.class);

    @Mock
    private Meta mockMeta;
    @Mock
    private AttributeMapFactory attributeMapFactory;
    @Mock
    private MetaHolder mockMetaHolder;
    @InjectMocks
    private MetaStream metaStream;

    @Test
    void call() throws Exception {
        Mockito.when(getMockXPathContext().getConfiguration()).thenReturn(Configuration.newConfiguration());

        Mockito.when(mockMeta.getId()).thenReturn(1L);
        Mockito.when(mockMetaHolder.getPartIndex()).thenReturn(0L);
        Mockito.when(mockMetaHolder.getMeta()).thenReturn(mockMeta);

        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("Feed", "myFeed");
        attributeMap.put("Action", "myAction");

        Mockito.when(attributeMapFactory.getAttributeMapForPart(1L, 0L)).thenReturn(attributeMap);

        final Sequence sequence = callFunctionWithSimpleArgs();
        assertThat(sequence).isNotNull();

        final String xml = getAsSerialisedXmlString(sequence).orElseThrow();

        assertThat(xml).isEqualToIgnoringWhitespace("""
                <meta-stream>
                   <string key="Action">myAction</string>
                   <string key="Feed">myFeed</string>
                </meta-stream>
                """);
    }

    @Override
    MetaStream getXsltFunction() {
        return metaStream;
    }

    @Override
    String getFunctionName() {
        return MetaStream.FUNCTION_NAME_NO_ARGS;
    }
}
