package stroom.importexport.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.importexport.client.event.ExportConfigEvent;
import stroom.importexport.client.presenter.ExportConfigOptionsPresenter.ExportConfigOptionsView;
import stroom.importexport.shared.ContentResource;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

public class ExportConfigOptionsPresenter
        extends MyPresenterWidget<ExportConfigOptionsView> {

    private static final ContentResource CONTENT_RESOURCE = GWT.create(ContentResource.class);

    private final DocRefListPresenter docRefListPresenter;

    @Inject
    public ExportConfigOptionsPresenter(final EventBus eventBus,
                                        final ExportConfigOptionsView view,
                                        final DocRefListPresenter docRefListPresenter,
                                        final RestFactory restFactory) {
        super(eventBus, view);
        this.docRefListPresenter = docRefListPresenter;

        restFactory
                .create(CONTENT_RESOURCE)
                .method(ContentResource::fetchSingletons)
                .onSuccess(docRefs -> {
                    docRefListPresenter.setData(NullSafe.stream(docRefs.getDocRefs())
                            .sorted(Comparator.comparing(DocRef::getName))
                            .collect(Collectors.toList()));
                })
                .taskMonitorFactory(this)
                .exec();
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setSingletonDocsPicker(docRefListPresenter.getWidget());
    }

    public Set<DocRef> getSelectedDocRefs() {
        return docRefListPresenter.getSelectedDocRefs();
    }

    public boolean isIncludeProcFilters() {
        return getView().isIncludeProcFilters();
    }

    void onExportEvent(final ExportConfigEvent event) {
//        NullSafe.forEach(event.getSelection(), );
//        if (NullSafe.hasItems(event.getSelection())) {
//
//            for (final ExplorerNode node : event.getSelection()) {
//                treePresenter.getTreeModel().setEnsureVisible(new HashSet<>(event.getSelection()));
//                treePresenter.setSelected(node, true);
//            }
//        }
    }


    // --------------------------------------------------------------------------------


    public interface ExportConfigOptionsView extends View {

        boolean isIncludeProcFilters();

        void setIncludeProcFilters(final boolean isIncludeProcFilters);

        void setSingletonDocsPicker(final Widget widget);
    }
}
