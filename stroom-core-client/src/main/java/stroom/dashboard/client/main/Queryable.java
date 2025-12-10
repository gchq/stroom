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

package stroom.dashboard.client.main;

import stroom.dashboard.client.query.QueryInfo;
import stroom.query.api.ResultStoreInfo;
import stroom.query.client.presenter.SearchErrorListener;
import stroom.query.client.presenter.SearchStateListener;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ErrorMessage;

import java.util.List;

public interface Queryable {

    void addSearchStateListener(SearchStateListener listener);

    void removeSearchStateListener(SearchStateListener listener);

    void addSearchErrorListener(SearchErrorListener listener);

    void removeSearchErrorListener(SearchErrorListener listener);

    boolean getSearchState();

    List<ErrorMessage> getCurrentErrors();

    void setQueryInfo(QueryInfo queryInfo);

    void start();

    void stop();

    void setQueryOnOpen(boolean queryOnOpen);

    void setDashboardContext(DashboardContext dashboardContext);

    void setResultStoreInfo(ResultStoreInfo resultStoreInfo);

    void setTaskMonitorFactory(TaskMonitorFactory taskMonitorFactory);
}
