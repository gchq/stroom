/*
 * Copyright 2016 Crown Copyright
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

package stroom.item.client.view;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewImpl;
import stroom.item.client.presenter.ListPresenter.ListView;

public class ListViewImpl extends ViewImpl implements ListView {
    private FlowPanel layout;

    public ListViewImpl() {
        layout = new FlowPanel();
    }

    @Override
    public Widget asWidget() {
        return layout;
    }

    @Override
    public void add(final View item) {
        layout.add(item.asWidget());
    }

    @Override
    public void clear() {
        layout.clear();
    }
}
