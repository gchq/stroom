/*
 * Copyright 2023 Crown Copyright
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

package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;

import javax.inject.Provider;

public class DocTabProvider<D>
        implements TabProvider<D> {

    private final Provider<DocPresenter<?, D>> presenterWidgetProvider;

    private DocPresenter<?, D> widget;

    public DocTabProvider(final Provider<DocPresenter<?, D>> presenterWidgetProvider) {
        this.presenterWidgetProvider = presenterWidgetProvider;
    }

    @Override
    public DocPresenter<?, D> getPresenter() {
        if (widget == null) {
            widget = presenterWidgetProvider.get();
        }
        return widget;
    }

    @Override
    public void read(final DocRef docRef, final D document) {
        getPresenter().read(docRef, document);
    }

    @Override
    public D write(final D document) {
        return getPresenter().write(document);
    }

    @Override
    public void onClose() {
        getPresenter().onClose();
    }
}
