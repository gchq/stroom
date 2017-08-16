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

package stroom.dashboard.client.vis;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import stroom.entity.client.presenter.HasReadAndWrite;
import stroom.item.client.StringListBox;
import stroom.util.client.JSONUtil;
import stroom.widget.customdatebox.client.MyDateBox;
import stroom.widget.tab.client.presenter.Layer;
import stroom.widget.tab.client.presenter.LayerContainer;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import java.util.ArrayList;
import java.util.List;

public class DynamicSettingsPane extends Composite implements Layer, HasReadAndWrite<JSONObject> {
    private final SimplePanel outer;
    private final List<StringListBox> fieldControls = new ArrayList<>();
    private final List<HasReadAndWrite<JSONObject>> controls = new ArrayList<>();
    private double opacity;

    public DynamicSettingsPane() {
        outer = new SimplePanel();
        initWidget(outer);
    }

    public void addControls(final JSONArray controls) {
        final Grid grid = new Grid(controls.size(), 2);
        grid.setStyleName("stroom-control-grid");

        for (int i = 0; i < controls.size(); i++) {
            final JSONObject control = JSONUtil.getObject(controls.get(i));

            final String label = JSONUtil.getString(control.get("label"));
            final Widget widget = createWidget(control);

            grid.setText(i, 0, label + ":");
            grid.setWidget(i, 1, widget);
        }

        outer.setWidget(grid);
    }

    private Widget createWidget(final JSONObject control) {
        final String id = JSONUtil.getString(control.get("id"));
        final String type = JSONUtil.getString(control.get("type"));
        final String defaultValue = JSONUtil.getString(control.get("defaultValue"));

        Widget widget = null;

        if ("field".equals(type)) {
            final StringListBox ctrl = createStringListBox(id);
            widget = ctrl;

            fieldControls.add(ctrl);

        } else if ("selection".equals(type)) {
            final StringListBox ctrl = createStringListBox(id);
            widget = ctrl;

            final String[] values = JSONUtil.getStrings(control.get("values"));

            if (values != null) {
                for (final String val : values) {
                    ctrl.addItem(val);
                }
            }

            if (defaultValue != null) {
                ctrl.setSelected(defaultValue);
            }
        } else if ("text".equals(type)) {
            final TextBox ctrl = createTextBox(id);
            widget = ctrl;

            if (defaultValue != null) {
                ctrl.setText(defaultValue);
            }
        } else if ("date".equals(type)) {
            final MyDateBox ctrl = createDateBox(id);
            widget = ctrl;

            if (defaultValue != null) {
                ctrl.setValue(defaultValue);
            }
        } else if ("number".equals(type)) {
            final ValueSpinner ctrl = createNumberBox(id);
            widget = ctrl;

            final Long min = getLong(JSONUtil.getString(control.get("min")));
            final Long max = getLong(JSONUtil.getString(control.get("max")));
            final Long minStep = getLong(JSONUtil.getString(control.get("minStep")));
            final Long maxStep = getLong(JSONUtil.getString(control.get("maxStep")));

            if (min != null) {
                ctrl.setMin(min);
            }
            if (max != null) {
                ctrl.setMax(max);
            }
            if (minStep != null) {
                ctrl.setMinStep(minStep.intValue());
            }
            if (maxStep != null) {
                ctrl.setMaxStep(maxStep.intValue());
            }

            if (defaultValue != null) {
                final Long l = getLong(defaultValue);
                if (l != null) {
                    ctrl.setValue(l);
                }
            }
        } else if ("boolean".equals(type)) {
            final TickBox ctrl = createBooleanBox(id);
            widget = ctrl;

            if (defaultValue != null) {
                final Boolean b = getBoolean(defaultValue);
                if (b != null) {
                    ctrl.setBooleanValue(b);
                }
            }
        }

        return widget;
    }

    private StringListBox createStringListBox(final String id) {
        final StringListBox ctrl = new StringListBox();
        ctrl.getElement().getStyle().setWidth(100, Unit.PCT);

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void read(final JSONObject settings) {
                final String val = JSONUtil.getString(settings.get(id));
                if (val != null) {
                    ctrl.setSelected(val);
                }
            }

            @Override
            public void write(final JSONObject settings) {
                final String selected = ctrl.getSelected();
                if (selected != null && selected.length() > 0) {
                    settings.put(id, new JSONString(selected));
                }
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private TextBox createTextBox(final String id) {
        final TextBox ctrl = new TextBox();
        ctrl.getElement().getStyle().setWidth(100, Unit.PCT);

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void read(final JSONObject settings) {
                final String val = JSONUtil.getString(settings.get(id));
                if (val != null) {
                    ctrl.setText(val);
                }
            }

            @Override
            public void write(final JSONObject settings) {
                final String val = ctrl.getText();
                if (val != null && val.trim().length() > 0) {
                    settings.put(id, new JSONString(val.trim()));
                }
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private MyDateBox createDateBox(final String id) {
        final MyDateBox ctrl = new MyDateBox();
        ctrl.getElement().getStyle().setWidth(100, Unit.PCT);

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void read(final JSONObject settings) {
                ctrl.setValue(JSONUtil.getString(settings.get(id)));
            }

            @Override
            public void write(final JSONObject settings) {
                final String val = ctrl.getValue();
                if (val != null && val.trim().length() > 0) {
                    settings.put(id, new JSONString(val.trim()));
                }
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private ValueSpinner createNumberBox(final String id) {
        final ValueSpinner ctrl = new ValueSpinner();
        ctrl.getElement().getStyle().setWidth(100, Unit.PCT);

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void read(final JSONObject settings) {
                final Long l = getLong(JSONUtil.getString(settings.get(id)));
                if (l != null) {
                    ctrl.setValue(l);
                }
            }

            @Override
            public void write(final JSONObject settings) {
                final long l = ctrl.getValue();
                settings.put(id, new JSONString(Long.toString(l)));
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private TickBox createBooleanBox(final String id) {
        final TickBox ctrl = new TickBox();
        ctrl.getElement().getStyle().setWidth(100, Unit.PCT);

        final HasReadAndWrite<JSONObject> hasReadAndWrite = new HasReadAndWrite<JSONObject>() {
            @Override
            public void read(final JSONObject settings) {
                final Boolean b = getBoolean(JSONUtil.getString(settings.get(id)));
                if (b != null) {
                    ctrl.setBooleanValue(b);
                }
            }

            @Override
            public void write(final JSONObject settings) {
                final boolean b = ctrl.getBooleanValue();
                settings.put(id, new JSONString(Boolean.toString(b)));
            }
        };
        controls.add(hasReadAndWrite);

        return ctrl;
    }

    private Long getLong(final String val) {
        if (val != null) {
            try {
                final long l = Long.parseLong(val);
                return l;
            } catch (final Exception e) {
                // Ignore
            }
        }
        return null;
    }

    private Boolean getBoolean(final String val) {
        if (val != null) {
            try {
                final boolean b = Boolean.parseBoolean(val);
                return b;
            } catch (final Exception e) {
                // Ignore
            }
        }
        return null;
    }

    public void setFieldNames(final List<String> fieldNames) {
        for (final StringListBox ctrl : fieldControls) {
            final String selected = ctrl.getSelected();
            ctrl.clear();
            ctrl.addItems(fieldNames);
            ctrl.setSelected(selected);
        }
    }

    @Override
    public void read(final JSONObject settings) {
        for (final HasReadAndWrite<JSONObject> control : controls) {
            control.read(settings);
        }
    }

    @Override
    public void write(final JSONObject settings) {
        for (final HasReadAndWrite<JSONObject> control : controls) {
            control.write(settings);
        }
    }

    @Override
    public double getOpacity() {
        return opacity;
    }

    /**************
     * Start Layer
     **************/
    @Override
    public void setOpacity(final double opacity) {
        this.opacity = opacity;
        getElement().getStyle().setOpacity(opacity);
    }

    @Override
    public void addLayer(final LayerContainer layerContainer) {
        setOpacity(opacity);
        layerContainer.add(this);
    }

    @Override
    public boolean removeLayer() {
        removeFromParent();
        return true;
    }

    @Override
    public void onResize() {
        // if (asWidget() instanceof RequiresResize) {
        // ((RequiresResize) asWidget()).onResize();
        // }
    }

    /**************
     * End Layer
     **************/
}
