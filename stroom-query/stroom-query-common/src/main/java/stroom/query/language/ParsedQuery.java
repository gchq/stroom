/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.language;

import stroom.query.api.Query;
import stroom.query.api.TableSettings;

/// A bracketed query in StroomQL, as parsed by [SearchRequestFactory]: its search, and its eval,
/// grouping and select.
///
/// @param query         The search of the bracketed query.
/// @param tableSettings The eval, grouping and select of the bracketed query.
record ParsedQuery(Query query, TableSettings tableSettings) {

}
