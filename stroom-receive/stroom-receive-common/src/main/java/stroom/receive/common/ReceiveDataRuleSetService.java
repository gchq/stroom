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

package stroom.receive.common;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.meta.api.AttributeMapper;
import stroom.query.api.datasource.QueryField;
import stroom.receive.rules.shared.HashedReceiveDataRules;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;

import java.util.List;

public interface ReceiveDataRuleSetService {

    /**
     * @return The {@link ReceiveDataRules}. Some implementations may obfuscate expression
     * term values.
     */
    ReceiveDataRules getReceiveDataRules();

    ReceiveDataRules updateReceiveDataRules(final ReceiveDataRules receiveDataRules);

    /**
     * @return Obfuscated {@link ReceiveDataRules} wrapped in {@link HashedReceiveDataRules}
     * along with details of the obfuscation.
     */
    HashedReceiveDataRules getHashedReceiveDataRules();

    /**
     * @return The {@link ReceiveDataRules} along with a {@link WordListProvider} and
     * an {@link AttributeMapper} instance that will map attribute values in the same
     * way that the expression term values have been mapped, if at all. Essentially
     * everything needed to evaluate an {@link stroom.meta.api.AttributeMap} against
     * a rule set.
     * <p>
     * This method can be used by both stroom and proxy. Proxy will get a set of rules
     * and flattened {@link WordListProvider}s that have been obfuscated, but the
     * {@link AttributeMapper} in the bundle will be able to obfuscate attributes
     * in the same way.
     * </p>
     */
    BundledRules getBundledRules();


    // --------------------------------------------------------------------------------


    record BundledRules(ReceiveDataRules receiveDataRules,
                        WordListProvider wordListProvider,
                        AttributeMapper attributeMapper) {

        public List<QueryField> getFields() {
            return receiveDataRules.getFields();
        }

        public List<ReceiveDataRule> getRules() {
            return receiveDataRules.getRules();
        }

        public String[] getWords(final DocRef dictionaryRef) {
            return wordListProvider.getWords(dictionaryRef);
        }
    }
}
