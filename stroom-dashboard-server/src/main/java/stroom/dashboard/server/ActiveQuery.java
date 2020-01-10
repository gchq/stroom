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
 */

package stroom.dashboard.server;

import stroom.query.api.v2.DocRef;
import stroom.security.shared.UserIdentity;

class ActiveQuery {
    private final DocRef docRef;
    private final UserIdentity userIdentity;
    private final long creationTime;

    ActiveQuery(final DocRef docRef, final UserIdentity userIdentity) {
        this.docRef = docRef;
        this.userIdentity = userIdentity;
        this.creationTime = System.currentTimeMillis();
    }

    DocRef getDocRef() {
        return docRef;
    }

    UserIdentity getUserIdentity() {
        return userIdentity;
    }

    long getCreationTime() {
        return creationTime;
    }

    @Override
    public String toString() {
        return "ActiveQuery{" +
                "docRef=" + docRef +
                ", userIdentity=" + userIdentity +
                ", creationTime=" + creationTime +
                '}';
    }
}
