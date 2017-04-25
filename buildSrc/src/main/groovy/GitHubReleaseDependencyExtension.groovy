import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection

class GitHubReleaseDependencyExtension {
    def dependencies = [:]
    def files = [:]
    def libs = "libs"
    def attachTo

    void add(String name, String url){
        dependencies.put(name, url)
    }

    FileCollection getDepPath(String name) {
        return new SimpleFileCollection(new File("libs/${name}.jar"))
    }

    FileCollection getDepPathForGwt(String name) {
        return new SimpleFileCollection(new File("../libs/${name}.jar"))
    }
}