package stroom.planb.client.view;

import stroom.widget.form.client.FormLabel;

import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HasWidgets;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collections;
import java.util.Iterator;

public class SettingsGroup extends Composite implements HasWidgets {

    private final FlowPanel settingsGroupPanel = new FlowPanel();
    private final FormLabel formLabel = new FormLabel();
    private Widget childWidget = null;

    public SettingsGroup() {
        settingsGroupPanel.addStyleName("settings-group");
        formLabel.addStyleName("settings-group-label");
        initWidget(settingsGroupPanel);
        updateFormGroupPanel();
    }

    public void setLabel(final String label) {
        formLabel.setLabel(label);
    }

    public String getLabel() {
        return formLabel.getLabel();
    }

    @Override
    public void add(final Widget widget) {
        this.childWidget = widget;
        widget.addStyleName("allow-focus");
        updateFormGroupPanel();
    }

    @Override
    public void clear() {
        childWidget = null;
        updateFormGroupPanel();
    }

    private void updateFormGroupPanel() {
        settingsGroupPanel.clear();
        settingsGroupPanel.add(formLabel);
        if (childWidget != null) {
            settingsGroupPanel.add(childWidget);
        }
    }

    @Override
    public Iterator<Widget> iterator() {
        return Collections.singleton(childWidget).iterator();
    }

    @Override
    public boolean remove(final Widget w) {
        if (childWidget == w) {
            clear();
            return true;
        } else {
            return false;
        }
    }
}
