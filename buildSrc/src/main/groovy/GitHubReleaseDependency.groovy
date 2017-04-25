import org.gradle.api.Plugin
import org.gradle.api.Project

class GitHubReleaseDependency implements Plugin<Project> {
    void apply(Project project) {

        project.extensions.create("gitHubDeps", GitHubReleaseDependencyExtension)
        project.task('hello') {
            doLast {
                println "Hello from the GitHubReleaseDependency"
            }
        }

        project.task('setupFileDependencies') {
            shouldRunAfter 'assemble'
            doLast {
                for(dependency in project.gitHubDeps.dependencies){
                    download(dependency.value, "libs/${dependency.key}.jar")

                    project.gitHubDeps.files.put(dependency.key, "libs/${dependency.key}.jar")
                }
            }
        }
    }

    def download(String remoteUrl, String localUrl) {
        // TODO This works for getting the content length
//        URLConnection connection = new URL(remoteUrl).openConnection()
//        def contentLength = connection.contentLength

        def file = new File(localUrl)
        if(!file.exists()) {
            new File("$localUrl").withOutputStream { out ->
                new URL(remoteUrl).withInputStream { from -> out << from; }
            }
        }
    }
}


