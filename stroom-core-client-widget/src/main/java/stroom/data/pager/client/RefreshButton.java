package stroom.data.pager.client;

import stroom.svg.client.SvgPresets;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.button.client.SvgButton;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;

public class RefreshButton extends Composite {

    private final FlowPanel layout;
    private final SvgButton refresh;

    public RefreshButton() {
        refresh = SvgButton.create(SvgPresets.REFRESH_BLUE);

        final SimplePanel spinnerInner = new SimplePanel();
        spinnerInner.setStyleName("spinner-inner");
        spinnerInner.getElement().setInnerHTML(SvgImage.SPINNER_SPINNER.getSvg());
        final SimplePanel spinnerOuter = new SimplePanel(spinnerInner);
        spinnerOuter.setStyleName("spinner-outer");

        final SimplePanel spinningInner = new SimplePanel();
        spinningInner.setStyleName("spinning-inner");
        spinningInner.getElement().setInnerHTML(SvgImage.SPINNER_SPINNING.getSvg());
        final SimplePanel spinningOuter = new SimplePanel(spinningInner);
        spinningOuter.setStyleName("spinning-outer");

        final SimplePanel pauseInner = new SimplePanel();
        pauseInner.setStyleName("pause-inner");
        pauseInner.getElement().setInnerHTML(SvgImage.SPINNER_PAUSE.getSvg());
        final SimplePanel pauseOuter = new SimplePanel(pauseInner);
        pauseOuter.setStyleName("pause-outer");

        layout = new FlowPanel();
        layout.setStyleName("refreshButton");
        layout.add(refresh);
        layout.add(spinnerOuter);
        layout.add(spinningOuter);
        layout.add(pauseOuter);

//        spinnerSmall.addDomHandler(event -> {
//            if (getUiHandlers() != null) {
//                getUiHandlers().onPause();
//            }
//        }, ClickEvent.getType());
//        pause.addDomHandler(event -> {
//            if (getUiHandlers() != null) {
//                getUiHandlers().onPause();
//            }
//        }, ClickEvent.getType());

        initWidget(layout);
    }

    public void setRefreshing(final boolean refreshing) {
        if (refreshing) {
            layout.addStyleName("refreshing");
        } else {
            layout.removeStyleName("refreshing");
        }
    }

    public void setAllowPause(final boolean allowPause) {
        if (allowPause) {
            layout.addStyleName("allowPause");
        } else {
            layout.removeStyleName("allowPause");
        }
    }

    public void setPaused(final boolean paused) {
        if (paused) {
            layout.addStyleName("paused");
        } else {
            layout.removeStyleName("paused");
        }
    }

    public void setEnabled(final boolean enabled) {
        refresh.setEnabled(enabled);
    }

    public HandlerRegistration addClickHandler(ClickHandler handler) {
        return refresh.addClickHandler(handler);
    }
}
