/*
 * Copyright 2019 Crown Copyright
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

package stroom.search.extraction;

import stroom.alert.api.AlertDefinition;
import stroom.dashboard.expression.v1.FieldIndex;
import stroom.pipeline.filter.AbstractXMLFilter;
import stroom.query.api.v2.QueryKey;

import java.util.List;
import java.util.Map;

public abstract class AbstractSearchResultOutputFilter extends AbstractXMLFilter {

    QueryKey queryKey;
    List<AlertDefinition> alertDefinitions;
    Map<String, String> paramMapForAlerting;
    ExtractionReceiver receiver;
    FieldIndex fieldIndex;
    int count;

    public void setup(final QueryKey queryKey,
                      final ExtractionReceiver receiver) {
        this.queryKey = queryKey;
        this.receiver = receiver;
        this.fieldIndex = receiver.getFieldIndex();
    }

    public void setupForAlerting(final List<AlertDefinition> alertDefinitions,
                                 final Map<String, String> paramMap) {
        this.alertDefinitions = alertDefinitions;
        this.paramMapForAlerting = paramMap;
    }

    public int getCount() {
        return count;
    }
}
