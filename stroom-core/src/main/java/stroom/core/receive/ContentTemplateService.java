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

package stroom.core.receive;

import stroom.query.api.datasource.QueryField;
import stroom.receive.content.shared.ContentTemplates;

import java.util.Set;

public interface ContentTemplateService {

    ContentTemplates fetch();

    ContentTemplates update(final ContentTemplates contentTemplates);

    Set<QueryField> fetchFields();
}
