package stroom.servicediscovery.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class ServiceDiscoveryConfig extends AbstractConfig {
    private boolean enabled = false;
    private String zookeeperUrl = "localhost:2181";
    private String servicesHostNameOrIpAddress = "localhost";
    private int servicesPort = 8080;
    private int curatorBaseSleepTimeMs = 5000;
    private int curatorMaxSleepTimeMs = 300000;
    private int curatorMaxRetries = 100;
    private String zookeeperBasePath = "/stroom-services";

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("Set this to true to use Zookeeper for service discovery. Set this to false to use resolve all services locally, i.e. 127.0.0.1")
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("The Zookeeper quorum connection string, required for service discovery, in the form 'host1:port1,host2:port2,host3:port3'. The root znode to use in Zookeeper is defined in the property stroom.serviceDiscovery.zookeeperBasePath")
    public String getZookeeperUrl() {
        return zookeeperUrl;
    }

    public void setZookeeperUrl(final String zookeeperUrl) {
        this.zookeeperUrl = zookeeperUrl;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("The external facing address that stroom will register its services with service discovery. If this property is empty stroom will try to establish the hostname. Recommended to be left blank in production to avoid having host specific configuration, unless the hostname is that of a load balancer in front of stroom instances.")
    public String getServicesHostNameOrIpAddress() {
        return servicesHostNameOrIpAddress;
    }

    public void setServicesHostNameOrIpAddress(final String servicesHostNameOrIpAddress) {
        this.servicesHostNameOrIpAddress = servicesHostNameOrIpAddress;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("The external facing port that stroom will register its services with service discovery")
    public int getServicesPort() {
        return servicesPort;
    }

    public void setServicesPort(final int servicesPort) {
        this.servicesPort = servicesPort;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("Initial time in ms between retries to establish a connection to zookeeper")
    public int getCuratorBaseSleepTimeMs() {
        return curatorBaseSleepTimeMs;
    }

    public void setCuratorBaseSleepTimeMs(final int curatorBaseSleepTimeMs) {
        this.curatorBaseSleepTimeMs = curatorBaseSleepTimeMs;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("Maximum time in ms between retries to establish a connection to zookeeper")
    public int getCuratorMaxSleepTimeMs() {
        return curatorMaxSleepTimeMs;
    }

    public void setCuratorMaxSleepTimeMs(final int curatorMaxSleepTimeMs) {
        this.curatorMaxSleepTimeMs = curatorMaxSleepTimeMs;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("Maximum number of retries to establish a connection to zookeeper before giving up")
    public int getCuratorMaxRetries() {
        return curatorMaxRetries;
    }

    public void setCuratorMaxRetries(final int curatorMaxRetries) {
        this.curatorMaxRetries = curatorMaxRetries;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("The base path to use in zookeeper for Curator service discover. All services registering or querying discoverable services must use the same value for this base path. Must start with a '/'")
    public String getZookeeperBasePath() {
        return zookeeperBasePath;
    }

    public void setZookeeperBasePath(final String zookeeperBasePath) {
        this.zookeeperBasePath = zookeeperBasePath;
    }

    @Override
    public String toString() {
        return "ServiceDiscoveryConfig{" +
                "enabled=" + enabled +
                ", zookeeperUrl='" + zookeeperUrl + '\'' +
                ", servicesHostNameOrIpAddress='" + servicesHostNameOrIpAddress + '\'' +
                ", servicesPort=" + servicesPort +
                ", curatorBaseSleepTimeMs=" + curatorBaseSleepTimeMs +
                ", curatorMaxSleepTimeMs=" + curatorMaxSleepTimeMs +
                ", curatorMaxRetries=" + curatorMaxRetries +
                ", zookeeperBasePath='" + zookeeperBasePath + '\'' +
                '}';
    }
}
