package stroom.statistics.internal;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import stroom.properties.impl.mock.MockPropertyService;
import stroom.docref.DocRef;

import java.util.List;
import java.util.UUID;

public class TestInternalStatisticDocRefCache {

    private static final String statKey = "myStatKey";
    private static final String propKey = String.format(InternalStatisticDocRefCache.PROP_KEY_FORMAT, statKey);

    private static final String type1 = "myType1";
    private static final String uuid1 = UUID.randomUUID().toString();
    private static final String name1 = "myStatName";
    private static final String docRefStr1 = String.format("docRef(%s,%s,%s)", type1, uuid1, name1);
    private static final DocRef expectedDocRef1 = new DocRef(type1, uuid1, name1);

    private static final String type2 = "myType2";
    private static final String uuid2 = UUID.randomUUID().toString();
    private static final String name2 = "myStatName2";
    private static final String docRefStr2 = String.format("docRef(%s,%s,%s)", type2, uuid2, name2);
    private static final DocRef expectedDocRef2 = new DocRef(type2, uuid2, name2);

    @Test
    public void getDocRefs_singleDocRef() {

        MockPropertyService mockPropertyService = new MockPropertyService();

        String propValue = docRefStr1;
        mockPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockPropertyService);

        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);

        Assertions.assertThat(docRefs).hasSize(1);

        Assertions.assertThat(docRefs).containsExactly(expectedDocRef1);
    }

    @Test
    public void getDocRefs_twoDocRefs() {

        MockPropertyService mockPropertyService = new MockPropertyService();

        String propValue = docRefStr1 + "," + docRefStr2;
        mockPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockPropertyService);

        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);

        Assertions.assertThat(docRefs).hasSize(2);

        Assertions.assertThat(docRefs).containsExactly(expectedDocRef1, expectedDocRef2);
    }

    @Test
    public void getDocRefs_emptyProvVal() {

        MockPropertyService mockPropertyService = new MockPropertyService();

        String propValue = "";
        mockPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockPropertyService);

        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);

        Assertions.assertThat(docRefs).hasSize(0);
    }

    @Test
    public void getDocRefs_nullProvVal() {

        MockPropertyService mockPropertyService = new MockPropertyService();

        String propValue = null;
        mockPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockPropertyService);

        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);

        Assertions.assertThat(docRefs).hasSize(0);
    }

    @Test(expected = RuntimeException.class)
    public void getDocRefs_invalidPropVal() {

        MockPropertyService mockPropertyService = new MockPropertyService();

        String propValue = docRefStr1 + "xxx";
        mockPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockPropertyService);

        docRefCache.getDocRefs(statKey);
    }
}