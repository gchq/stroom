package stroom.util.io;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;

@NotInjectableConfig
public abstract class PathConfig extends AbstractConfig {

    private String home;
    private String temp;

//    public PathConfig(final String home, final String temp) {
//        this.home = home;
//        this.temp = temp;
//    }

    public String getHome() {
        return home;
    }

    public void setHome(final String home) {
        this.home = home;
    }

    public String getTemp() {
        return temp;
    }

    public void setTemp(final String temp) {
        this.temp = temp;
    }

    @Override
    public String toString() {
        return "PathConfig{" +
                "home='" + home + '\'' +
                ", temp='" + temp + '\'' +
                '}';
    }
}
