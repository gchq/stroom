/*
 * Copyright 2018 Crown Copyright
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

package stroom.refdata.lmdb.eval;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface KeyValueStore extends AutoCloseable {

    void put(final String key, final String value);

    void putBatch(final List<Map.Entry<String, String>> entries);

    Optional<String> get(final String key);

    Optional<String> getWithTxn(final String key);

    void clear();

}
