/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.ruleset.server;

import org.springframework.stereotype.Component;
import stroom.datafeed.server.MetaMapFilter;
import stroom.datafeed.server.MetaMapFilterFactory;
import stroom.dictionary.server.DictionaryService;

import javax.inject.Inject;

@Component
public class MetaMapFilterFactoryImpl implements MetaMapFilterFactory {
    private final RuleSetService ruleSetService;
    private final DictionaryService dictionaryService;

    @Inject
    public MetaMapFilterFactoryImpl(final RuleSetService ruleSetService, final DictionaryService dictionaryService) {
        this.ruleSetService = ruleSetService;
        this.dictionaryService = dictionaryService;
    }

    @Override
    public MetaMapFilter create(final String uuid) {
        return new MetaMapFilterImpl(new DataReceiptPolicyChecker(ruleSetService, dictionaryService, uuid));
    }
}
