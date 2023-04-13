package stroom.query.common.v2;

public interface KeyFactoryConfig {

    int getTimeFieldIndex();

    int getStreamIdFieldIndex();

    int getEventIdFieldIndex();

    boolean addTimeToKey();
}
