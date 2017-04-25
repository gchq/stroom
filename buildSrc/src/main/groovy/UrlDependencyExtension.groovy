import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection

class UrlDependencyExtension {
    def dependencies = [:]
    def files = [:]
    def libs = "libs"
    def attachTo

    void add(String name, String url){
        dependencies.put(name, url)
    }

    String getAsPath(String name){
        return "libs/${name}.jar"
    }

    FileCollection getAsFile(String path){
        return new SimpleFileCollection(new File(path))
    }

    FileCollection get(String name) {
        return new SimpleFileCollection(new File(getAsPath(name)))
    }


}