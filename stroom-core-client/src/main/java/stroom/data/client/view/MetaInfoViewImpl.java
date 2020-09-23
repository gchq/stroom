package stroom.data.client.view;

import stroom.data.client.presenter.MetaInfoPresenter.MetaInfoView;
import stroom.editor.client.presenter.HtmlPresenter;

import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

public class MetaInfoViewImpl extends ViewImpl implements MetaInfoView {

    @UiField
    HtmlPresenter container;

    @Override
    public void setContent(final SafeHtml safeHtml) {

    }

    @Override
    public Widget asWidget() {
        return this.asWidget();
    }
}
