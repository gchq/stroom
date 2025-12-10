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

package stroom.receive.rules.client.presenter;

import stroom.data.client.presenter.EditExpressionPresenter;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.client.presenter.DynamicFieldSelectionListModel;
import stroom.receive.rules.client.presenter.DataRetentionRulePresenter.DataRetentionRuleView;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.time.TimeUnit;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class DataRetentionRulePresenter extends MyPresenterWidget<DataRetentionRuleView> implements Focus {

    private final EditExpressionPresenter editExpressionPresenter;
    private final DynamicFieldSelectionListModel fieldSelectionBoxModel;
    private DataRetentionRule originalRule;

    @Inject
    public DataRetentionRulePresenter(final EventBus eventBus,
                                      final DataRetentionRuleView view,
                                      final EditExpressionPresenter editExpressionPresenter,
                                      final RestFactory restFactory,
                                      final DynamicFieldSelectionListModel fieldSelectionBoxModel) {
        super(eventBus, view);
        this.fieldSelectionBoxModel = fieldSelectionBoxModel;
        this.editExpressionPresenter = editExpressionPresenter;
        view.setExpressionView(editExpressionPresenter.getView());

        fieldSelectionBoxModel.setDataSourceRefConsumer(consumer -> consumer.accept(MetaFields.STREAM_STORE_DOC_REF));
        editExpressionPresenter.init(restFactory, MetaFields.STREAM_STORE_DOC_REF, fieldSelectionBoxModel);
    }

    @Override
    public void focus() {
        editExpressionPresenter.focus();
    }

    void read(final DataRetentionRule rule) {
        this.originalRule = rule;
        getView().setName(rule.getName());
        if (rule.getExpression() == null) {
            editExpressionPresenter.read(ExpressionOperator.builder().build());
        } else {
            editExpressionPresenter.read(rule.getExpression());
        }
        getView().setForever(rule.isForever());
        getView().setAge(rule.getAge());
        getView().setTimeUnit(rule.getTimeUnit());
    }

    DataRetentionRule write() {
        final ExpressionOperator expression = editExpressionPresenter.write();
        return new DataRetentionRule(
                originalRule.getRuleNumber(),
                originalRule.getCreationTime(),
                getView().getName(),
                originalRule.isEnabled(),
                expression,
                getView().getAge(),
                getView().getTimeUnit(),
                getView().isForever());
    }

    @Override
    public synchronized void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        super.setTaskMonitorFactory(taskMonitorFactory);
        fieldSelectionBoxModel.setTaskMonitorFactory(taskMonitorFactory);
    }

    public interface DataRetentionRuleView extends View {

        void setExpressionView(View view);

        String getName();

        void setName(String name);

        boolean isForever();

        void setForever(boolean forever);

        int getAge();

        void setAge(int age);

        TimeUnit getTimeUnit();

        void setTimeUnit(TimeUnit timeUnit);
    }
}
