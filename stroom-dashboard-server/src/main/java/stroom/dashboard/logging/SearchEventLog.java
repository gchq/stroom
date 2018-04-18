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

package stroom.dashboard.logging;

import stroom.query.api.v2.DocRef;
import stroom.query.api.v2.ExpressionOperator;

public interface SearchEventLog {
    void search(DocRef dataSourceRef, ExpressionOperator expression, String queryInfo);

    void search(DocRef dataSourceRef, ExpressionOperator expression, String queryInfo, Exception ex);

    void batchSearch(DocRef dataSourceRef, ExpressionOperator expression, String queryInfo);

    void batchSearch(DocRef dataSourceRef, ExpressionOperator expression, String queryInfo, Exception ex);

    void downloadResults(DocRef dataSourceRef, ExpressionOperator expression, String queryInfo);

    void downloadResults(DocRef dataSourceRef, ExpressionOperator expression, String queryInfo, Exception ex);

    void downloadResults(String type, DocRef dataSourceRef, ExpressionOperator expression, String queryInfo, Exception ex);

    void search(String type, DocRef dataSourceRef, ExpressionOperator expression, String queryInfo, Exception ex);
}
