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

package stroom.entity.client.presenter;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityServiceFindReferenceAction;
import stroom.widget.tooltip.client.presenter.TooltipPresenter;

public class EntityReferenceListPresenter extends MyPresenterWidget<DataGridView<DocRef>>
        implements HasRead<BaseEntity> {
    private final ClientDispatchAsync dispatcher;

    @Inject
    public EntityReferenceListPresenter(final EventBus eventBus, final ClientDispatchAsync dispatcher,
                                        final TooltipPresenter tooltipPresenter) {
        super(eventBus, new DataGridViewImpl<DocRef>(false));

        this.dispatcher = dispatcher;
        getView().addResizableColumn(new Column<DocRef, String>(new TextCell()) {
            @Override
            public String getValue(final DocRef ref) {
                return ref.getType();
            }
        }, "Type", 100);
        getView().addResizableColumn(new Column<DocRef, String>(new TextCell()) {
            @Override
            public String getValue(final DocRef ref) {
                return ref.getName();
            }
        }, "Name", 1000);
        getView().addEndColumn(new EndColumn<DocRef>());

    }

    @SuppressWarnings("unchecked")
    @Override
    public void read(final BaseEntity entity) {
        final EntityServiceFindReferenceAction<BaseEntity> action = new EntityServiceFindReferenceAction(entity);
        dispatcher.exec(action).onSuccess(result -> {
            getView().setRowData(0, result);
            getView().setRowCount(result.size(), true);
        });
    }
}
