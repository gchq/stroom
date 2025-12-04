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

package stroom.data.client.gin;

import stroom.data.client.presenter.MetaListPresenter;
import stroom.data.client.presenter.MetaPresenter;
import stroom.data.client.presenter.ProcessorTaskListPresenter;

import com.google.gwt.inject.client.AsyncProvider;

public interface StreamStoreGinjector {

    AsyncProvider<MetaPresenter> getStreamPresenter();

    AsyncProvider<MetaListPresenter> getStreamListPresenter();

    AsyncProvider<ProcessorTaskListPresenter> getStreamTaskListPresenter();
}
