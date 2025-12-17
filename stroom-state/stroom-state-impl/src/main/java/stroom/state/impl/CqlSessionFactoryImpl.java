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

package stroom.state.impl;

import stroom.docref.DocRef;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class CqlSessionFactoryImpl implements CqlSessionFactory {

    private final CqlSessionCache cqlSessionCache;

    @Inject
    public CqlSessionFactoryImpl(final CqlSessionCache cqlSessionCache) {
        this.cqlSessionCache = cqlSessionCache;
    }

    @Override
    public CqlSession getSession(final DocRef scyllaDbDocRef) {
        return cqlSessionCache.get(scyllaDbDocRef);
    }

    @Override
    public Provider<CqlSession> getSessionProvider(final DocRef scyllaDbDocRef) {
        return () -> getSession(scyllaDbDocRef);
    }
}
