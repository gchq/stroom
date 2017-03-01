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

package stroom.entity.shared;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StringCriteriaTest {
    @Test
    public void testConvertStringList() throws Exception {
        List<String> strings = new ArrayList<String>();
        strings.add("abcdef");
        strings.add("ABCDEF");

        List<StringCriteria> criteriaList = StringCriteria.convertStringList(strings);

        for (int i = 0; i < strings.size(); i++) {
            Assert.assertEquals(strings.get(i).toString(), criteriaList.get(i).toString());

        }

    }

}
