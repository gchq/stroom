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

package stroom.security.identity.token;

/**
 * What a call to {@link JwkDao#rotate} actually did.
 *
 * @param createdKeyId    The {@code kid} of the key now being signed with, or null if the active key
 *                        was young enough to keep.
 * @param retiredKeyId    The {@code kid} of the key that stopped being signed with but is still
 *                        published, or null if nothing was retired.
 * @param reconciledCount How many surplus active keys, from a lockless creation race, were retired.
 *                        Normally zero.
 * @param deletedCount    How many expired keys were removed.
 */
public record JwkRotationSummary(String createdKeyId,
                                 String retiredKeyId,
                                 int reconciledCount,
                                 int deletedCount) {

    public boolean rotated() {
        return createdKeyId != null;
    }
}
