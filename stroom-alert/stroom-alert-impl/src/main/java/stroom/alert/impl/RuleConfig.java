/*
 * Copyright 2020 Crown Copyright
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

package stroom.alert.impl;

import stroom.alert.api.AlertDefinition;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;

import java.util.List;
import java.util.Map;

interface RuleConfig {

    String getUuid();

    String getName();

    Map<String, String> getParams();

    ExpressionOperator getExpression();

    DocRef getExtractionPipeline();

    List<AlertDefinition> getAlertDefinitions();
}
