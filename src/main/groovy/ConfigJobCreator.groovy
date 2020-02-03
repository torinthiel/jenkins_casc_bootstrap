import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement

class ConfigJobCreator {
	private static final String CONFIG_SUBDIR="jenkins_casc_configuration"

	// Note that this script is interpreted TWICE by groovy:
	// Once when the class is parsed, second time when the script
	// is passed to DslScriptLoader and executed.
	// That's the reason some things are escaped with a single \ and some with
	// a double one, depending which level we need to protect from.
	private final String JOB_DSL_SCRIPT = """\
		def configJob = job('config') {
			label('master')
			scm {
				git {
					remote{
						url('${->repoPath}')
						credentials('ssh-key')
					}
					branch('*/experiments')
					extensions {
						cleanBeforeCheckout()
					}
				}
			}

			steps {
				shell('''\\
					TARGET_DIR=\$JENKINS_HOME/$CONFIG_SUBDIR

					mkdir -p \$TARGET_DIR
					cp *.yaml \$TARGET_DIR
				'''.stripIndent())
				systemGroovyCommand('''\\
					def jenkins = jenkins.model.Jenkins.get()
					def jcacPlugin = jenkins.getExtensionList(io.jenkins.plugins.casc.ConfigurationAsCode.class).first()
					jcacPlugin.configure("\${jenkins.rootDir}/$CONFIG_SUBDIR")
				'''.stripIndent())
			}
		}
		queue(configJob)
	""".stripIndent()

	def getRepoPath() {
		 return 'git@github.com:torinthiel/jenkins_casc_bootstrap.git'
	}

	public generateJobs() {
		System.out.println(JOB_DSL_SCRIPT)
		def workspace = new File('.')
		def jobManagement = new JenkinsJobManagement(System.out, [:], workspace)
		new DslScriptLoader(jobManagement).runScript(JOB_DSL_SCRIPT)
	}
}
