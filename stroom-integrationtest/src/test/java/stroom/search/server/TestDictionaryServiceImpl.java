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

package stroom.search.server;

import org.junit.Assert;
import org.junit.Test;
import stroom.AbstractCoreIntegrationTest;
import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.dictionary.shared.FindDictionaryCriteria;
import stroom.entity.shared.BaseResultList;

import javax.annotation.Resource;

public class TestDictionaryServiceImpl extends AbstractCoreIntegrationTest {
    @Resource
    private DictionaryService dictionaryService;

    @Test
    public void test() {
        // Create a dictionary and save it.
        final Dictionary dictionary = dictionaryService.create(null, "TEST");
        dictionary.setData("This\nis\na\nlist\nof\nwords");
        dictionaryService.save(dictionary);

        // Make sure we can get it back.
        final BaseResultList<Dictionary> list = dictionaryService.find(new FindDictionaryCriteria());
        Assert.assertEquals(1, list.size());
    }
}
