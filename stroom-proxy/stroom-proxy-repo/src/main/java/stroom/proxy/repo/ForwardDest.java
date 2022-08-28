package stroom.proxy.repo;

public class ForwardDest {
    private final int id;
    private final String name;

    public ForwardDest(final int id, final String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
