package stroom.refdata.saxevents;

public abstract class AbstractPoolValue {

//    public abstract short getTypeId();

    public abstract boolean equals(Object obj);

    public abstract int hashCode();

    public abstract byte[] getValueBytes();

}
