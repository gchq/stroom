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

package stroom.dictionary;

import stroom.dictionary.shared.Dictionary;
import stroom.dictionary.shared.DictionaryService;
import stroom.dictionary.shared.FindDictionaryCriteria;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.util.StroomEntityManager;
import stroom.security.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;

@Component("dictionaryService")
@Transactional
public class DictionaryServiceImpl extends DocumentEntityServiceImpl<Dictionary, FindDictionaryCriteria>
        implements DictionaryService {
    @Inject
    DictionaryServiceImpl(final StroomEntityManager entityManager, final SecurityContext securityContext) {
        super(entityManager, securityContext);
    }

    @Override
    public Class<Dictionary> getEntityClass() {
        return Dictionary.class;
    }

    @Override
    public FindDictionaryCriteria createCriteria() {
        return new FindDictionaryCriteria();
    }
}
