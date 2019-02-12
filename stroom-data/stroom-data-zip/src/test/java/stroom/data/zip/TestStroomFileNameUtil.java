package stroom.data.zip;


import org.junit.jupiter.api.Test;
import stroom.meta.shared.AttributeMap;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomFileNameUtil {
    @Test
    void testPad() {
        assertThat(StroomFileNameUtil.getIdPath(1)).isEqualTo("001");
        assertThat(StroomFileNameUtil.getIdPath(999)).isEqualTo("999");
        assertThat(StroomFileNameUtil.getIdPath(1000)).isEqualTo("001/001000");
        assertThat(StroomFileNameUtil.getIdPath(1999)).isEqualTo("001/001999");
        assertThat(StroomFileNameUtil.getIdPath(9111999)).isEqualTo("009/111/009111999");
    }

    @Test
    void testConstructFilename() {
        AttributeMap attributeMap = new AttributeMap();
        attributeMap.put("feed", "myFeed");
        attributeMap.put("var1", "myVar1");

        final String standardTemplate = "${pathId}/${id}";
        final String staticTemplate = "${pathId}/${id} someStaticText";
        final String dynamicTemplate = "${id} ${var1} ${feed}";

        final String extension1 = ".zip";
        final String extension2 = ".bad";

        assertThat(StroomFileNameUtil.constructFilename(1, standardTemplate, attributeMap, extension1, extension2)).isEqualTo("001.zip.bad");
        assertThat(StroomFileNameUtil.constructFilename(3000, standardTemplate, attributeMap, extension1)).isEqualTo("003/003000.zip");
        assertThat(StroomFileNameUtil.constructFilename(3000, dynamicTemplate, attributeMap, extension1)).isEqualTo("003000_myVar1_myFeed.zip");
        assertThat(StroomFileNameUtil.constructFilename(3000, staticTemplate, attributeMap, extension1)).isEqualTo("003/003000_someStaticText.zip");
        assertThat(StroomFileNameUtil.constructFilename(3000, staticTemplate, attributeMap)).isEqualTo("003/003000_someStaticText");
    }
}