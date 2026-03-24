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

import stroom.receive.common.DataFeedIdentity.IdentityStatus;

import java.nio.file.Path;

/**
 * Service for dealing with Data Feed Keys and other data feed identities, e.g. Certificate
 * Identities.
 * <p>
 * This allows data feed identities to be placed in a file on the file system and these
 * identities can be used to authenticate data feed receipt and to inject additional
 * metadata in the received data's attributes.
 * </p>
 */
public interface DataFeedKeyService extends AuthenticatorFilter {

    IdentityStatus addDataFeedKey(HashedDataFeedKey hashedDataFeedKey,
                                  Path sourceFile);

    void removeKeysForFile(Path sourceFile);
}
