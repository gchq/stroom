package stroom.widget.datepicker.client;

import com.google.gwt.aria.client.Roles;
import com.google.gwt.aria.client.SelectedValue;

public final class DateCell extends AbstractCell {

    private boolean enabled = true;
    private final int index;

    private final DefaultCalendarView defaultCalendarView;
    private final CustomDatePicker.StandardCss css;
    private String cellStyle;
    private String dateStyle;
    private JsDate value = JsDate.create();


    public DateCell(final DefaultCalendarView defaultCalendarView,
                    final CustomDatePicker.StandardCss css,
                    final boolean isWeekend,
                    final int index) {
        this.defaultCalendarView = defaultCalendarView;
        this.css = css;
        this.index = index;
        cellStyle = css.day();

        if (isWeekend) {
            cellStyle += " " + css.dayIsWeekend();
        }
        getElement().setTabIndex(isFiller()
                ? -1
                : 0);
        setAriaSelected(false);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSelected() {
        final JsDate selected = defaultCalendarView.getDatePicker().getValue();
        return selected != null && value != null && value.getTime() == selected.getTime();
    }

    public boolean isHighlighted() {
        final JsDate highlighted = defaultCalendarView.getDatePicker().getHighlightedDate();
        return highlighted != null && value != null && value.getTime() == highlighted.getTime();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        onEnabled(enabled);
    }

    private void onEnabled(boolean enabled) {
        updateStyle();
    }

    private void updateStyle() {
        String accum = dateStyle;
        if (isHighlighted()) {
            accum += " " + css.dayIsHighlighted();
            if (isSelected()) {
                accum += " " + css.dayIsValueAndHighlighted();
            }
        }
        if (!isEnabled()) {
            accum += " " + css.dayIsDisabled();
        }
        setStyleName("cellOuter " + accum);
    }

    @Override
    public void addStyleName(String styleName) {
        if (dateStyle.indexOf(" " + styleName + " ") == -1) {
            dateStyle += styleName + " ";
        }
        updateStyle();
    }

    public boolean isFiller() {
        return !defaultCalendarView.getModel().isInCurrentMonth(value);
    }

    public void onSelected(boolean selected) {
        if (selected) {
            defaultCalendarView.getDatePicker().setValue(value, true);
            if (isFiller()) {
                defaultCalendarView.getDatePicker().setCurrentMonth(value);
            }
        }
        updateStyle();
    }

    @Override
    public void removeStyleName(String styleName) {
        dateStyle = dateStyle.replace(" " + styleName + " ", " ");
        updateStyle();
    }

    public void setAriaSelected(boolean value) {
        Roles.getGridcellRole().setAriaSelectedState(getElement(), SelectedValue.of(value));
    }

    public JsDate getValue() {
        return value;
    }

    void update(final JsDate current) {
        setEnabled(true);
        value = CalendarUtil.copyDate(current);
        String text = defaultCalendarView.getModel().formatDayOfMonth(value);
        setText(text);
        dateStyle = cellStyle;
        if (isFiller()) {
            getElement().setTabIndex(-1);
            dateStyle += " " + css.dayIsFiller();
        } else {
            getElement().setTabIndex(0);
            String extraStyle = defaultCalendarView.getDatePicker().getStyleOfDate(value);
            if (extraStyle != null) {
                dateStyle += " " + extraStyle;
            }
        }
        // We want to certify that all date styles have " " before and after
        // them for ease of adding to and replacing them.
        dateStyle += " ";
        updateStyle();
    }

    public int getIndex() {
        return index;
    }
}
