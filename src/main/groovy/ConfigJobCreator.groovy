import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement

class ConfigJobCreator {
	// Note that this script is interpreted TWICE by groovy:
	// Once when the class is parsed, second time when the script
	// is passed to DslScriptLoader and executed.
	// Because of this, the ''' inside this string have to be escaped
	// or use """, but each $ inside needs to be escaped by double-backslash.
	private static final String JOB_DSL_SCRIPT = '''\
		def configJob = job('config') {
			label('master')
			scm {
				git {
					remote{
						url('git@github.com:torinthiel/jenkins_casc_bootstrap.git')
						credentials('ssh-key')
					}
					branch('*/experiments')
					extensions {
						cleanBeforeCheckout()
					}
				}
			}

			steps {
				shell("""\\
					TARGET_DIR=\\$JENKINS_HOME/jenkins_casc_configuration

					mkdir -p \\$TARGET_DIR
					cp *.yaml \\$TARGET_DIR
				""".stripIndent())
				systemGroovyCommand("""\\
					import jenkins.model.Jenkins
					def jcacPlugin = Jenkins.instance.getExtensionList(io.jenkins.plugins.casc.ConfigurationAsCode.class).first()
					jcacPlugin.configure("/var/jenkins_home/jenkins_casc_configuration")
				""".stripIndent())
			}
		}
		queue(configJob)
	'''.stripIndent()

	public generateJobs() {
		System.out.println(JOB_DSL_SCRIPT)
		def workspace = new File('.')
		def jobManagement = new JenkinsJobManagement(System.out, [:], workspace)
		new DslScriptLoader(jobManagement).runScript(JOB_DSL_SCRIPT)
	}
}
