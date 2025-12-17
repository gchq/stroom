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

package stroom.query.common.v2;

import stroom.node.api.NodeInfo;
import stroom.query.api.SearchRequestSource;
import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

import java.util.Objects;

public final class ResultStoreFactory {

    private final SizesProvider sizesProvider;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final ResultStoreSettingsFactory resultStoreSettingsFactory;
    private final MapDataStoreFactory mapDataStoreFactory;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    ResultStoreFactory(final SizesProvider sizesProvider,
                       final SecurityContext securityContext,
                       final NodeInfo nodeInfo,
                       final ResultStoreSettingsFactory resultStoreSettingsFactory,
                       final MapDataStoreFactory mapDataStoreFactory,
                       final ExpressionPredicateFactory expressionPredicateFactory) {
        this.sizesProvider = sizesProvider;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.resultStoreSettingsFactory = resultStoreSettingsFactory;
        this.mapDataStoreFactory = mapDataStoreFactory;
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    public ResultStore create(final SearchRequestSource searchRequestSource,
                              final CoprocessorsImpl coprocessors) {
        final UserRef userRef = securityContext.getUserRef();
        Objects.requireNonNull(userRef, "No user is logged in");

        return new ResultStore(
                searchRequestSource,
                sizesProvider,
                userRef,
                coprocessors,
                nodeInfo.getThisNodeName(),
                resultStoreSettingsFactory.get(),
                mapDataStoreFactory,
                expressionPredicateFactory);
    }
}
