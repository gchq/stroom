package stroom.externaldoc.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.cell.clickable.client.Hyperlink;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.entity.shared.SharedDocRef;
import stroom.security.client.ClientSecurityContext;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.UrlConfig;
import stroom.widget.iframe.client.presenter.IFrameContentPresenter;

import java.util.HashMap;
import java.util.Map;

public class ExternalDocRefPresenter
        extends DocumentEditPresenter<IFrameContentPresenter.IFrameContentView, SharedDocRef>
        implements DocumentTabData {
    private static final String ANNOTATIONS_INDEX = "AnnotationsIndex";
    private static final String ELASTIC_INDEX = "ElasticIndex";

    private final IFrameContentPresenter settingsPresenter;
    private Map<String, String> uiUrls;
    private DocRef docRef;

    @Inject
    public ExternalDocRefPresenter(final EventBus eventBus,
                                   final IFrameContentPresenter iFramePresenter,
                                   final ClientSecurityContext securityContext,
                                   final UiConfigCache clientPropertyCache) {
        super(eventBus, iFramePresenter.getView(), securityContext);
        this.settingsPresenter = iFramePresenter;

        clientPropertyCache.get()
                .onSuccess(result -> {
                    uiUrls = new HashMap<>();
                    if (result.getUrlConfig() != null) {
                        final UrlConfig urlConfig = result.getUrlConfig();
                        if (urlConfig.getAnnotations() != null && urlConfig.getAnnotations().length() > 0) {
                            uiUrls.put(ANNOTATIONS_INDEX, urlConfig.getAnnotations());
                        }
                        if (urlConfig.getElastic() != null && urlConfig.getElastic().length() > 0) {
                            uiUrls.put(ELASTIC_INDEX, urlConfig.getElastic());
                        }
                    }
                })
                .onFailure(caught -> AlertEvent.fireError(ExternalDocRefPresenter.this, caught.getMessage(), null));
    }

    @Override
    protected void onRead(final DocRef docRef, final SharedDocRef document) {
        this.docRef = docRef;
        final Hyperlink hyperlink = new Hyperlink.HyperlinkBuilder()
                .href(this.uiUrls.get(document.getType()) + "/" + document.getUuid())
                .build();
        this.settingsPresenter.setIcon(getIcon());
        this.settingsPresenter.setHyperlink(hyperlink);
    }

    @Override
    protected void onWrite(final SharedDocRef annotationsIndex) {

    }

    @Override
    public String getType() {
        return docRef.getType();
    }

    @Override
    public Icon getIcon() {
        if (null != docRef) {
            switch (docRef.getType()) {
                case ANNOTATIONS_INDEX:
                    return SvgPresets.ANNOTATIONS;
                case ELASTIC_INDEX:
                    return SvgPresets.ELASTIC_SEARCH;
            }
        }

        return null;
    }

    @Override
    public String getLabel() {
        return docRef.getName();
    }

    @Override
    public boolean isCloseable() {
        return true;
    }
}
