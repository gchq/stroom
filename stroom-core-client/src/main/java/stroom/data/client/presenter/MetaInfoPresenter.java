package stroom.data.client.presenter;

import stroom.data.client.presenter.MetaInfoPresenter.MetaInfoView;
import stroom.data.shared.DataInfoSection;
import stroom.data.shared.DataResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class MetaInfoPresenter extends MyPresenterWidget<MetaInfoView> {

    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);

    private final RestFactory restFactory;
    private long metaId;

    public MetaInfoPresenter(final EventBus eventBus,
                             final MetaInfoView view,
                             final RestFactory restFactory) {
        super(eventBus, view);
        this.restFactory = restFactory;
    }

    public void setMetaId(final long metaId) {
        this.metaId = metaId;
        fetchData();
    }

    private void fetchData() {
        final Rest<List<DataInfoSection>> rest = restFactory.create();
        rest
                .onSuccess(this::handleResult)
                .call(DATA_RESOURCE)
                .info(metaId);
    }

    private void handleResult(final List<DataInfoSection> dataInfoSections) {
        final TooltipUtil.Builder builder = TooltipUtil.builder();

        builder.addTable(tableBuilder -> {
            for (int i = 0; i < dataInfoSections.size(); i++) {
                final DataInfoSection section = dataInfoSections.get(i);
                tableBuilder.addHeaderRow(section.getTitle());
                section.getEntries()
                        .forEach(entry ->
                                tableBuilder.addRow(entry.getKey(), entry.getValue()));
                if (i < dataInfoSections.size() - 1) {
                    tableBuilder.addBlankRow();
                }
            }
            return tableBuilder.build();
        });

        getView().setContent(builder.build());
    }

    public static interface MetaInfoView extends View {

        void setContent(final SafeHtml safeHtml);
    }
}
