package stroom.entity.client.presenter;

import com.google.web.bindery.event.shared.EventBus;

import javax.inject.Provider;

public abstract class MarkdownTabProvider<E> extends AbstractTabProvider<E, MarkdownEditPresenter> {

    private final Provider<MarkdownEditPresenter> markdownEditPresenterProvider;

    public MarkdownTabProvider(final EventBus eventBus,
                               final Provider<MarkdownEditPresenter> markdownEditPresenterProvider) {
        super(eventBus);
        this.markdownEditPresenterProvider = markdownEditPresenterProvider;
    }

    @Override
    protected final MarkdownEditPresenter createPresenter() {
        final MarkdownEditPresenter markdownEditPresenter = markdownEditPresenterProvider.get();
        registerHandler(markdownEditPresenter.addDirtyHandler(event -> fireDirtyEvent(true)));
        return markdownEditPresenter;
    }
}
