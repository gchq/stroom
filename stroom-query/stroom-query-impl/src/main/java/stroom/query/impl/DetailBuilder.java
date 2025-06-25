package stroom.query.impl;

import java.util.function.Consumer;

public class DetailBuilder extends AbstractHtmlBuilder<DetailBuilder> {

    public DetailBuilder() {
        startElem("div");
    }

    public DetailBuilder title(final String title) {
        elem("b", b -> b.append(title));
        emptyElem("hr");
        return self();
    }

    public DetailBuilder description(final Consumer<DetailBuilder> consumer) {
        elem("p", "queryHelpDetail-description", consumer);
        return self();
    }

    public DetailBuilder table(final Consumer<DetailBuilder> consumer) {
        elem("table", consumer);
        return self();
    }

    public DetailBuilder appendKVRow(final String key, final String value) {
        elem("tr", tr -> {
            tr.elem("td", td -> td.elem("b", b -> b.append(key)));
            tr.elem("td", td -> td.append(value));
        });
        return self();
    }

    @Override
    public DetailBuilder self() {
        return this;
    }

    public String build() {
        endElem("div");
        return toString();
    }
}
