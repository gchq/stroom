package stroom.query.impl;

import java.util.function.Consumer;

public class DetailBuilder extends HtmlBuilder {
    
    public DetailBuilder() {
        startElem("div");
    }

    public DetailBuilder title(String title) {
        elem("b", b -> b.append(title));
        emptyElem("hr");
        return this;
    }

    public DetailBuilder description(final Consumer<DetailBuilder> consumer) {
        startElem("p", "queryHelpDetail-description");
        consumer.accept(this);
        endElem("p");
        return this;
    }

    public DetailBuilder table(final Consumer<DetailBuilder> consumer) {
        startElem("table");
        consumer.accept(this);
        endElem("table");
        return this;
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
        return this;
    }

    public String build() {
        endElem("div");
        return toString();
    }
}
