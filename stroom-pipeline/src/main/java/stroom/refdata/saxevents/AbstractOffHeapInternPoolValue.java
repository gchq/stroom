package stroom.refdata.saxevents;

public abstract class AbstractOffHeapInternPoolValue {

    public abstract short getTypeId();

    public abstract boolean equals(Object obj);

    public abstract int hashCode();

    public abstract byte[] getValueBytes();

}
