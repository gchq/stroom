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

package stroom.statistics.impl;

//

import org.junit.jupiter.api.Disabled;

@Disabled
class TestInternalStatisticDocRefCache {

//    private static final String statKey = "myStatKey";
//    private static final String propKey = String.format(InternalStatisticDocRefCache.PROP_KEY_FORMAT, statKey);
//
//    private static final String type1 = "myType1";
//    private static final String uuid1 = UUID.randomUUID().toString();
//    private static final String name1 = "myStatName";
//    private static final String docRefStr1 = String.format("docRef(%s,%s,%s)", type1, uuid1, name1);
//    private static final DocRef expectedDocRef1 = new DocRef(type1, uuid1, name1);
//
//    private static final String type2 = "myType2";
//    private static final String uuid2 = UUID.randomUUID().toString();
//    private static final String name2 = "myStatName2";
//    private static final String docRefStr2 = String.format("docRef(%s,%s,%s)", type2, uuid2, name2);
//    private static final DocRef expectedDocRef2 = new DocRef(type2, uuid2, name2);
//
//    @Test
//    public void getDocRefs_singleDocRef() {
//        final InternalStatisticsConfig internalStatisticsConfig = new InternalStatisticsConfig();
//
//        String propValue = docRefStr1;
//        mockStroomPropertyService.setProperty(propKey, propValue);
//
//        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(internalStatisticsConfig);
//
//        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);
//
//        assertThat(docRefs).hasSize(1);
//
//        assertThat(docRefs).containsExactly(expectedDocRef1);
//    }
//
//    @Test
//    public void getDocRefs_twoDocRefs() {
//        final InternalStatisticsConfig internalStatisticsConfig = new InternalStatisticsConfig();
//
//        String propValue = docRefStr1 + "," + docRefStr2;
//        mockStroomPropertyService.setProperty(propKey, propValue);
//
//        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(internalStatisticsConfig);
//
//        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);
//
//        assertThat(docRefs).hasSize(2);
//
//        assertThat(docRefs).containsExactly(expectedDocRef1, expectedDocRef2);
//    }
//
//    @Test
//    public void getDocRefs_emptyProvVal() {
//        final InternalStatisticsConfig internalStatisticsConfig = new InternalStatisticsConfig();
//
//        String propValue = "";
//        mockStroomPropertyService.setProperty(propKey, propValue);
//
//        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(internalStatisticsConfig);
//
//        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);
//
//        assertThat(docRefs).hasSize(0);
//    }
//
//    @Test
//    public void getDocRefs_nullProvVal() {
//        final InternalStatisticsConfig internalStatisticsConfig = new InternalStatisticsConfig();
//
//        String propValue = null;
//        mockStroomPropertyService.setProperty(propKey, propValue);
//
//        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(internalStatisticsConfig);
//
//        List<DocRef> docRefs = docRefCache.getDocRefs(statKey);
//
//        assertThat(docRefs).hasSize(0);
//    }
//
//    @Test
//    void getDocRefs_invalidPropVal() {
//          assertThatThrownBy(() -> {
//        final InternalStatisticsConfig internalStatisticsConfig = new InternalStatisticsConfig();
//
//        String propValue = docRefStr1 + "xxx";
//        mockStroomPropertyService.setProperty(propKey, propValue);
//
//        InternalStatisticDocRefCache docRefCache = new InternalStatisticDocRefCache(internalStatisticsConfig);
//
//        docRefCache.getDocRefs(statKey);
//          }).isInstanceOf(RuntimeException .class);
//    }
}
