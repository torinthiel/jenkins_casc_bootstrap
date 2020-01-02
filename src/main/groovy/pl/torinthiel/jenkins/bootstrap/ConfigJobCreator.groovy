package pl.torinthiel.jenkins.bootstrap

import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement

class ConfigJobCreator {
	private static final String CONFIG_SUBDIR="jenkins_casc_configuration"

	// Note that this script is interpreted TWICE by groovy:
	// Once when the class is parsed, second time when the script
	// is passed to DslScriptLoader and executed.
	// That's the reason some things are escaped with a single \ and some with
	// a double one, depending which level we need to protect from.
	private final GString JOB_DSL_SCRIPT = """\
		def configJob = job('config') {
			label('master')
			scm {
				git {
					remote{
						url('${->retrieve(VaultConfigKey.REPO_URL)}')
						credentials('${->retrieve(VaultConfigKey.SSH_ID)}')
					}
					branch('*/${->retrieve(VaultConfigKey.REPO_BRANCH)}')
					extensions {
						cleanBeforeCheckout()
					}
				}
			}

			steps {
				shell('''\\
					TARGET_DIR=\$JENKINS_HOME/$CONFIG_SUBDIR

					mkdir -p \$TARGET_DIR
					for dir in ${->retrieve(VaultConfigKey.REPO_DIRECTORIES).replace(',', ' ')}; do
						if [ -d \$dir ]; then
							cp \$dir/*.yaml \$TARGET_DIR
						fi
					done
				'''.stripIndent())
				systemGroovyCommand('''\\
					def jenkins = jenkins.model.Jenkins.get()
					def jcacPlugin = jenkins.getExtensionList(io.jenkins.plugins.casc.ConfigurationAsCode.class).first()
					jcacPlugin.configure("\${jenkins.rootDir}/$CONFIG_SUBDIR")
				'''.stripIndent())
			}
		}
		queue(configJob)
	"""

	private VaultAccessor accessor

	ConfigJobCreator(VaultAccessor accessor) {
		this.accessor = accessor
	}

	def retrieve(VaultConfigKey key) {
		accessor.getValue(key)
	}

	public generateJobs() {
		System.out.println(JOB_DSL_SCRIPT)
		def workspace = new File('.')
		def jobManagement = new JenkinsJobManagement(System.out, [:], workspace)
		new DslScriptLoader(jobManagement).runScript(JOB_DSL_SCRIPT)
	}
}
