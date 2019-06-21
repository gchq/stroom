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
import stroom.datafeed.server.DataReceiptPolicyMetaMapFilterFactory;
import stroom.datafeed.server.MetaMapFilter;
import stroom.dictionary.server.DictionaryStore;
import stroom.streamstore.server.ExpressionMatcherFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

@Component
@Singleton
public class DataReceiptPolicyMetaMapFilterFactoryImpl implements DataReceiptPolicyMetaMapFilterFactory {
    private final RuleSetService ruleSetService;
    private final ExpressionMatcherFactory expressionMatcherFactory;

    @Inject
    public DataReceiptPolicyMetaMapFilterFactoryImpl(final RuleSetService ruleSetService, final ExpressionMatcherFactory expressionMatcherFactory) {
        this.ruleSetService = ruleSetService;
        this.expressionMatcherFactory = expressionMatcherFactory;
    }

    @Override
    public MetaMapFilter create(final String dataReceiptPolicyUuid) {
        return new DataReceiptPolicyMetaMapFilter(new DataReceiptPolicyChecker(ruleSetService, expressionMatcherFactory, dataReceiptPolicyUuid));
    }
}
