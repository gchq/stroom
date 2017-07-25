/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.testclient.presenter;

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.shared.EntityIdSet;
import stroom.streamstore.client.presenter.StreamFilterPresenter;
import stroom.streamstore.shared.Stream;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

public class TestStreamFilterPresenter {
    @Test
    public void testParse() {
        doTestParse("1", Arrays.asList(1L));
        doTestParse("", new HashSet<>());
        doTestParse(" ", new HashSet<>());
        doTestParse("1 ", Arrays.asList(1L));
        doTestParse("1 , 2", Arrays.asList(1L, 2L));
        doTestParse("1  ,  4", Arrays.asList(1L, 4L));
    }

    public void doTestParse(final String text, final Collection<Long> ids) {
        final EntityIdSet<Stream> streamIdSet = new EntityIdSet<>();
        StreamFilterPresenter.stringToIdSet(text, streamIdSet);
        Assert.assertEquals(new HashSet<>(ids), streamIdSet.getSet());
        final String newString = StreamFilterPresenter.idSetToString(streamIdSet);
        StreamFilterPresenter.stringToIdSet(newString, streamIdSet);
        Assert.assertEquals(new HashSet<>(ids), streamIdSet.getSet());
    }
}
