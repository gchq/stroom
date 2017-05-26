package stroom.internalstatistics;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import stroom.node.server.MockStroomPropertyService;
import stroom.query.api.v1.DocRef;

import java.util.List;
import java.util.UUID;

public class TestInternalStatisticDocRefCache {

    private static final String statKey = "myStatKey";
    private static final String propKey = String.format(InternalStatisticDocRefCache.PROP_KEY_FORMAT, statKey);

    private static final String type1 = "myType1";
    private static final String uuid1 = UUID.randomUUID().toString();
    private static final String name1 = "myStatName";
    private static final String docRefStr1 = String.format("docRef(%s,%s,%s)",type1, uuid1, name1);
    private static final DocRef expectedDocRef1 = new DocRef(type1, uuid1, name1);

    private static final String type2 = "myType2";
    private static final String uuid2 = UUID.randomUUID().toString();
    private static final String name2 = "myStatName2";
    private static final String docRefStr2 = String.format("docRef(%s,%s,%s)",type2, uuid2, name2);
    private static final DocRef expectedDocRef2 = new DocRef(type2, uuid2, name2);

    @Test
    public void getDocRefs_singleDocRef() throws Exception {

        MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

        String propValue = docRefStr1;
        mockStroomPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockStroomPropertyService);

        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);

        Assertions.assertThat(docRefs).hasSize(1);

        Assertions.assertThat(docRefs).containsExactly(expectedDocRef1);
    }

    @Test
    public void getDocRefs_twoDocRefs() throws Exception {

        MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

        String propValue = docRefStr1 + "," + docRefStr2;
        mockStroomPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockStroomPropertyService);

        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);

        Assertions.assertThat(docRefs).hasSize(2);

        Assertions.assertThat(docRefs).containsExactly(expectedDocRef1, expectedDocRef2);
    }

    @Test
    public void getDocRefs_emptyProvVal() throws Exception {

        MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

        String propValue = "";
        mockStroomPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockStroomPropertyService);

        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);

        Assertions.assertThat(docRefs).hasSize(0);
    }

    @Test
    public void getDocRefs_nullProvVal() throws Exception {

        MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

        String propValue = null;
        mockStroomPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockStroomPropertyService);

        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);

        Assertions.assertThat(docRefs).hasSize(0);
    }

    @Test(expected = RuntimeException.class)
    public void getDocRefs_invalidPropVal() throws Exception {

        MockStroomPropertyService mockStroomPropertyService = new MockStroomPropertyService();

        String propValue = docRefStr1 + "xxx";
        mockStroomPropertyService.setProperty(propKey, propValue);

        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(mockStroomPropertyService);

        docRefCache.getDocRefs(statKey);
    }
}