import org.gradle.api.Plugin
import org.gradle.api.Project

class UrlDependency implements Plugin<Project> {
    void apply(Project project) {

        project.extensions.create("urlDependencies", UrlDependencyExtension)

        project.task('downloadUrlDependencies') {
            shouldRunAfter 'assemble'
            doLast {
                for(dependency in project.urlDependencies.dependencies){
                    download(dependency.value, "libs/${dependency.key}.jar")

                    project.urlDependencies.files.put(dependency.key, "libs/${dependency.key}.jar")
                }
            }
        }
    }

    def download(String remoteUrl, String localUrl) {
        // TODO This works for getting the content length - using this would make the selective download more
        // sophisticated - the user wouldn't have to delete the files to re-download. But if the deps
        // are named with version this probably won't be a big issue.
//        URLConnection connection = new URL(remoteUrl).openConnection()
//        def contentLength = connection.contentLength

        def file = new File(localUrl)
        if(!file.exists()) {
            new File("$localUrl").withOutputStream { out ->
                new URL(remoteUrl).withInputStream { from -> out << from }
            }
        }
    }
}


