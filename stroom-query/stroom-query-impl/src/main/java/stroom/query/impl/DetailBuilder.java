package stroom.query.impl;

import java.util.function.Consumer;

public class DetailBuilder extends AbstractHtmlBuilder<DetailBuilder> {
    
    public DetailBuilder() {
        startElem("div");
    }

    public DetailBuilder title(String title) {
        elem("b", b -> b.append(title));
        emptyElem("hr");
        return self();
    }

    public DetailBuilder description(final Consumer<DetailBuilder> consumer) {
        startElem("p", "queryHelpDetail-description");
        consumer.accept(this);
        endElem("p");
        return self();
    }

    public DetailBuilder table(final Consumer<DetailBuilder> consumer) {
        startElem("table");
        consumer.accept(this);
        endElem("table");
        return self();
    }

    public DetailBuilder appendKVRow(final String key, final String value) {
        startElem("tr");
        startElem("td");
        startElem("b");
        append(key);
        endElem("b");
        endElem("td");
        startElem("td");
        append(value);
        endElem("td");
        endElem("tr");
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
