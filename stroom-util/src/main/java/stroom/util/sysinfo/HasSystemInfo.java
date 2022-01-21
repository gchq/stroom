package stroom.util.sysinfo;

public interface HasSystemInfo {

    /**
     * @return The name for the system information being provided. Should be limited
     * to [A-Za-z_-] to avoid URL encoding issues. By default the qualified class
     * name will be used.
     */
    default String getSystemInfoName() {
        return this.getClass().getName();
    }

    /**
     * @return A {@link SystemInfoResult} for part of the system. e.g. for dumping debug information,
     * the sizes of in memory collections/queues, etc.
     * Implementations do not need to perform permission checks unless additional permissions beyond
     * VIEW_SYSTEM_INFO_PERMISSION are required.
     */
    SystemInfoResult getSystemInfo();
}
