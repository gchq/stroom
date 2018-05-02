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

package stroom.dashboard.client.table;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.shared.ValidateExpressionAction;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.query.shared.Field;
import stroom.util.shared.EqualsUtil;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.MenuListPresenter;
import stroom.widget.menu.client.presenter.SimpleParentMenuItem;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.ArrayList;
import java.util.List;

public class ExpressionPresenter extends MyPresenterWidget<ExpressionPresenter.ExpressionView>
        implements ExpressionUiHandlers {
    private final MenuListPresenter menuListPresenter;
    private final ClientDispatchAsync dispatcher;
    private List<Item> menuItems;
    private TablePresenter tablePresenter;
    private Field field;

    @Inject
    public ExpressionPresenter(final EventBus eventBus, final ExpressionView view,
                               final MenuListPresenter menuListPresenter, final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.menuListPresenter = menuListPresenter;
        this.dispatcher = dispatcher;
        view.setUiHandlers(this);
    }

    public void show(final TablePresenter tablePresenter, final Field field) {
        this.tablePresenter = tablePresenter;
        this.field = field;

        if (field.getExpression() != null) {
            getView().setExpression(field.getExpression());
        } else {
            getView().setExpression("");
        }

        final PopupSize popupSize = new PopupSize(500, 300, 300, 300, true);
        ShowPopupEvent.fire(tablePresenter, this, PopupType.OK_CANCEL_DIALOG, popupSize,
                "Set Expression For '" + field.getName() + "'", this);
        Scheduler.get().scheduleDeferred(() -> getView().focus());
    }

    @Override
    public void onHideRequest(final boolean autoClose, final boolean ok) {
        if (ok) {
            final String expression = getView().getExpression();
            if (EqualsUtil.isEquals(expression, field.getExpression())) {
                HidePopupEvent.fire(tablePresenter, this);
            } else {
                if (expression == null) {
                    field.setExpression(null);
                    tablePresenter.setDirty(true);
                    tablePresenter.clearAndRefresh();
                    HidePopupEvent.fire(tablePresenter, this);
                } else {
                    // Check the validity of the expression.
                    dispatcher.exec(new ValidateExpressionAction(expression)).onSuccess(result -> {
                        if (result.isOk()) {
                            field.setExpression(expression);
                            tablePresenter.setDirty(true);
                            tablePresenter.clearAndRefresh();
                            HidePopupEvent.fire(tablePresenter, ExpressionPresenter.this);
                        } else {
                            AlertEvent.fireError(tablePresenter, result.getString(), null);
                        }
                    });
                }
            }
        } else {
            HidePopupEvent.fire(tablePresenter, this);
        }
    }

    @Override
    public void onHide(final boolean autoClose, final boolean ok) {
    }

    @Override
    public void onAddFunction(final ClickEvent event) {
        if ((event.getNativeButton() & NativeEvent.BUTTON_LEFT) != 0) {
            final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
                @Override
                public void onHideRequest(final boolean autoClose, final boolean ok) {
                    HidePopupEvent.fire(ExpressionPresenter.this, menuListPresenter);
                }

                @Override
                public void onHide(final boolean autoClose, final boolean ok) {
                }
            };

            if (menuItems == null) {
                menuItems = createMenuItems();
            }

            final com.google.gwt.dom.client.Element target = event.getNativeEvent().getEventTarget().cast();
            final PopupPosition popupPosition = new PopupPosition(target.getAbsoluteLeft() - 3,
                    target.getAbsoluteTop() + target.getClientHeight() + 1);

            menuListPresenter.setData(menuItems);

            ShowPopupEvent.fire(this, menuListPresenter, PopupType.POPUP, popupPosition, popupUiHandlers);
        }
    }

    private List<Item> createMenuItems() {
        final List<Item> children = new ArrayList<>();
        int pos = 0;
        children.add(createRowFunctons(pos++, "Row Functions..."));
        children.add(createAggregateFunctons(pos++, "Aggregate Functions..."));
        return children;
    }

    private Item createRowFunctons(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createCastSubMenuItems(item++, "Cast"));
        children.add(createDateSubMenuItems(item++, "Date"));
        children.add(createLogicSubMenuItems(item++, "Logic"));
        children.add(createMathematicsSubMenuItems(item++, "Mathematics"));
        children.add(createStringSubMenuItems(item++, "String"));
        children.add(createUriSubMenuItems(item++, "Uri"));
        children.add(createValueSubMenuItems(item++, "Value"));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createCastSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "toBoolean($)", "toBoolean("));
        children.add(createFunction(item++, "toDouble($)", "toDouble("));
        children.add(createFunction(item++, "toInteger($)", "toInteger("));
        children.add(createFunction(item++, "toLong($)", "toLong("));
        children.add(createFunction(item++, "toString($)", "toString("));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createDateSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "formatDate($,pattern,timeZone)", "formatDate("));
        children.add(createFunction(item++, "parseDate($,pattern,timeZone)", "parseDate("));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createLogicSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "equals($,$)", "equals("));
        children.add(createFunction(item++, "greaterThan($,$)", "greaterThan("));
        children.add(createFunction(item++, "greaterThanOrEqualTo($,$)", "greaterThanOrEqualTo("));
        children.add(createFunction(item++, "if($,then,else)", "if("));
        children.add(createFunction(item++, "lessThan($,$)", "lessThan("));
        children.add(createFunction(item++, "lessThanOrEqualTo($,$)", "lessThanOrEqualTo("));
        children.add(createFunction(item++, "not($)", "not("));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createMathematicsSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "average($)", "average("));
        children.add(createCommonSubMenuItems(item++, "ceiling"));
        children.add(createCommonSubMenuItems(item++, "floor"));
        children.add(createFunction(item++, "max($)", "max("));
        children.add(createFunction(item++, "min($)", "min("));
        children.add(createFunction(item++, "random()", "random()"));
        children.add(createCommonSubMenuItems(item++, "round"));
        children.add(createFunction(item++, "stDev($)", "stDev("));
        children.add(createFunction(item++, "sum($)", "sum("));
        children.add(createFunction(item++, "variance($)", "variance("));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createCommonSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, func + "($,[n])", func + "("));
        children.add(createFunction(item++, func + "Year($)", func + "Year("));
        children.add(createFunction(item++, func + "Month($)", func + "Month("));
        children.add(createFunction(item++, func + "Day($)", func + "Day("));
        children.add(createFunction(item++, func + "Hour($)", func + "Hour("));
        children.add(createFunction(item++, func + "Minute($)", func + "Minute("));
        children.add(createFunction(item++, func + "Second($)", func + "Second("));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createStringSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "concat($,$)", "concat("));
        children.add(createFunction(item++, "decode($,[search,replace]...,otherwise)", "decode("));
        children.add(createFunction(item++, "exclude($,[regex...])", "exclude("));
        children.add(createFunction(item++, "hash($,algorithm)", "hash("));
        children.add(createFunction(item++, "include($,[regex...])", "include("));
        children.add(createFunction(item++, "indexOf($,string)", "indexOf("));
        children.add(createFunction(item++, "lastIndexOf($,string)", "lastIndexOf("));
        children.add(createFunction(item++, "lowerCase($)", "lowerCase("));
        children.add(createFunction(item++, "match($,regex)", "match("));
        children.add(createFunction(item++, "replace($,regex,replacement)", "replace("));
        children.add(createFunction(item++, "stringLength($)", "stringLength("));
        children.add(createFunction(item++, "substring($,startPos,endPos)", "substring("));
        children.add(createFunction(item++, "substringAfter($,string)", "substringAfter("));
        children.add(createFunction(item++, "substringBefore($,string)", "substringBefore("));
        children.add(createFunction(item++, "upperCase($)", "upperCase("));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createUriSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "extractAuthorityFromUri($)", "extractAuthorityFromUri("));
        children.add(createFunction(item++, "extractFragmentFromUri($)", "extractFragmentFromUri("));
        children.add(createFunction(item++, "extractHostFromUri($)", "extractHostFromUri("));
        children.add(createFunction(item++, "extractPathFromUri($)", "extractPathFromUri("));
        children.add(createFunction(item++, "extractPortFromUri($)", "extractPortFromUri("));
        children.add(createFunction(item++, "extractSchemeFromUri($)", "extractSchemeFromUri("));
        children.add(createFunction(item++, "extractSchemeSpecificPartFromUri($)", "extractSchemeSpecificPartFromUri("));
        children.add(createFunction(item++, "extractQueryFromUri($)", "extractQueryFromUri("));
        children.add(createFunction(item++, "extractUserInfoFromUri($)", "extractUserInfoFromUri("));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createValueSubMenuItems(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "err()", "err()"));
        children.add(createFunction(item++, "false()", "false()"));
        children.add(createFunction(item++, "null()", "null()"));
        children.add(createFunction(item++, "true()", "true()"));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createAggregateFunctons(final int pos, final String func) {
        final List<Item> children = new ArrayList<>();
        int item = 0;
        children.add(createFunction(item++, "average($)", "average("));
        children.add(createFunction(item++, "count()", "count()"));
        children.add(createFunction(item++, "countGroups()", "countGroups()"));
        children.add(createFunction(item++, "countUnique($)", "countUnique("));
        children.add(createFunction(item++, "max($)", "max("));
        children.add(createFunction(item++, "min($)", "min("));
        children.add(createFunction(item++, "sum($)", "sum("));
        return new SimpleParentMenuItem(pos, null, null, func, null, true, children);
    }

    private Item createFunction(final int pos, final String text, final String func) {
        return new IconMenuItem(pos, text, null, true, () -> addFunction(func));
    }

    private void addFunction(final String func) {
        String expression = getView().getExpression();
        if (expression != null && expression.trim().length() > 0) {
            final int cursorPos = getView().getCursorPos();
            final int selectionLength = getView().getSelectionLength();
            if (cursorPos >= 0) {
                if (cursorPos >= expression.length()) {
                    expression = expression + func;
                    getView().setExpression(expression);
                    getView().setCursorPos(expression.length());
                } else {
                    final String before = expression.substring(0, cursorPos);
                    final String after = expression.substring(cursorPos + selectionLength);
                    expression = before + func + after;
                    getView().setExpression(expression);
                    getView().setCursorPos((before + func).length());
                }
            } else {
                expression = func + expression;
                getView().setExpression(expression);
                getView().setCursorPos(func.length());
            }
        } else {
            expression = func;
            getView().setExpression(expression);
            getView().setCursorPos(expression.length());
        }
    }

    public interface ExpressionView extends View, HasUiHandlers<ExpressionUiHandlers> {
        String getExpression();

        void setExpression(String expression);

        int getCursorPos();

        void setCursorPos(int pos);

        int getSelectionLength();

        void focus();
    }
}
