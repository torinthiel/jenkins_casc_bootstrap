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
		def name = '${->retrieve(VaultConfigKey.JOB_NAME)}'
		def index = -1
		while ((index = name.indexOf('/', index + 1)) >= 0) {
			folder(name.substring(0, index))
		}

		def configJob = job(name) {
			description('${->retrieve(VaultConfigKey.JOB_DESCRIPTION)}')
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

			def scmPollingSchedule = '${->retrieve(VaultConfigKey.JOB_POLL_SCHEDULE)}'
			if (scmPollingSchedule) {
				triggers {
					scm(scmPollingSchedule)
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
				.replace('\\', '\\\\')
				.replace('\'', '\\\'')
				.replace('\n', '\\n')
	}

	public generateJobs() {
		System.out.println(JOB_DSL_SCRIPT)
		def workspace = new File('.')
		def jobManagement = new JenkinsJobManagement(System.out, [:], workspace)
		new DslScriptLoader(jobManagement).runScript(JOB_DSL_SCRIPT)
	}
}
