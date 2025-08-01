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

package stroom.query.client.presenter;

import stroom.query.api.GroupSelection;
import stroom.query.api.OffsetRange;
import stroom.query.api.Result;

public interface ResultComponent {

    OffsetRange getRequestedRange();

    GroupSelection getGroupSelection();

    void reset();

    void startSearch();

    void endSearch();

    void setData(Result componentResult);

    void setQueryModel(QueryModel queryModel);
}
