package stroom.pathways.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.pathways.client.presenter.PathwaysSplitPresenter.PathwaysSplitView;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.pathway.Pathway;
import stroom.util.shared.NullSafe;
import stroom.widget.util.client.ElementUtil;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.client.ui.HTML;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;

public class PathwaysSplitPresenter extends DocumentEditPresenter<PathwaysSplitView, PathwaysDoc> {

    private final PathwayListPresenter pathwayListPresenter;

    private Element selected;

    @Inject
    public PathwaysSplitPresenter(final EventBus eventBus,
                                  final PathwaysSplitView view,
                                  final PathwayListPresenter pathwayListPresenter) {
        super(eventBus, view);
        this.pathwayListPresenter = pathwayListPresenter;
        view.setTable(pathwayListPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(pathwayListPresenter.getSelectionModel().addSelectionHandler(e -> {
            final Pathway selected = pathwayListPresenter.getSelectionModel().getSelected();
            getView().setDetails(new PathwayTree().build(selected));
        }));
        registerHandler(getView().getDetails().addClickHandler(e -> {
            final Element target = e.getNativeEvent().getEventTarget().cast();
            if (target != null) {
                final Element node = ElementUtil.findParent(target, element ->
                        NullSafe.isNonBlankString(element.getAttribute("uuid")), 3);
                if (node != null) {
                    final String uuid = node.getAttribute("uuid");
//                    AlertEvent.fireInfo(this, uuid, null);

                    if (!Objects.equals(selected, node)) {
                        if (selected != null) {
                            selected.removeClassName("pathway-nodeName--selected");
                        }
                        selected = node;
                        selected.addClassName("pathway-nodeName--selected");
                    }
                }
            }
        }));
    }

    @Override
    protected void onRead(final DocRef docRef, final PathwaysDoc document, final boolean readOnly) {
        pathwayListPresenter.onRead(docRef, document, readOnly);
    }

    @Override
    protected PathwaysDoc onWrite(final PathwaysDoc document) {
        return pathwayListPresenter.onWrite(document);
    }

    public interface PathwaysSplitView extends View {

        void setTable(View view);

        void setDetails(SafeHtml html);

        HTML getDetails();
    }
}
