package pl.torinthiel.jenkins.bootstrap

import static java.util.logging.Level.INFO

import java.util.logging.Logger;
import jenkins.model.Jenkins;

public class CascBootstrap {
	private Logger log = Logger.getLogger(getClass().name)

	public main() {
		def config = new ConfigRetriever()
		def jenkins = Jenkins.get()

		VaultAccessor accessor = new VaultAccessor(config, jenkins)
		accessor.configureVault()
		log.log(INFO, "Connected to vault")

		CredentialUpdater updater = new CredentialUpdater(jenkins, accessor)
		updater.updateCredentials()
		log.log(INFO, "Configured credentials")

		ConfigJobCreator creator = new ConfigJobCreator(accessor)
		creator.generateJobs()
		log.log(INFO, "Created configuration job")
	}
}
