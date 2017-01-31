package stroom.search.server;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Path("hellos")
public class HelloResource {
    Map<String, Hello> database;

    public HelloResource() {
        database = new HashMap<>();
        Hello hello1 = new Hello("1", "ronan");
        Hello hello2 = new Hello("2", "john");

        database.put(hello1.getId(), hello1);
        database.put(hello2.getId(), hello2);
    }

    @GET
    @Produces("application/json")
    public Collection<Hello> get() {
        return database.values();
    }

    @GET
    @Path("/{id}")
    @Produces("application/json")
    public Hello getHello(@PathParam("id") String id) {
        return database.get(id);
    }

    @XmlRootElement
    public class Hello {
        @XmlElement(name = "id")
        private final String id;
        @XmlElement(name = "name")
        private final String name;

        public Hello(final String id, final String name) {
            this.id = id;
            this.name = name;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
