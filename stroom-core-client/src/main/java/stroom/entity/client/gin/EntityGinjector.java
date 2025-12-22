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

package stroom.entity.client.gin;

import stroom.entity.client.presenter.CopyDocumentPresenter;
import stroom.entity.client.presenter.CreateDocumentPresenter;
import stroom.entity.client.presenter.InfoDocumentPresenter;
import stroom.entity.client.presenter.MoveDocumentPresenter;
import stroom.entity.client.presenter.NameDocumentPresenter;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.inject.client.Ginjector;

public interface EntityGinjector extends Ginjector {

    AsyncProvider<CreateDocumentPresenter> getCreateEntityPresenter();

    AsyncProvider<CopyDocumentPresenter> getCopyEntityPresenter();

    AsyncProvider<MoveDocumentPresenter> getMoveEntityPresenter();

    AsyncProvider<NameDocumentPresenter> getRenameEntityPresenter();

    AsyncProvider<InfoDocumentPresenter> getInfoEntityPresenter();
}
