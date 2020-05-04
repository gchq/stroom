package stroom.util.sysinfo;

public interface HasSystemInfo {

    /**
     * @return The name for the system information being provided. Should be limited
     * to [A-Za-z_-] to avoid URL encoding issues. By default the unqualified class
     * name will be used.
     */
    default String getSystemInfoName() {
        return this.getClass().getSimpleName();
    }

    SystemInfoResult getSystemInfo();
}
