package stroom.proxy.repo.queue;

import org.jooq.DSLContext;

public interface WriteQueue {

    void flush(DSLContext context);

    int size();

    void clear();
}
