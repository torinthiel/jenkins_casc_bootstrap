import static java.util.logging.Level.INFO

import java.util.logging.Logger;
import jenkins.model.Jenkins;

public class CascBootstrap {
	Logger log = Logger.getLogger(getClass().name)

	public main() {
		def config = new ConfigRetriever()

		VaultAccessor accessor = new VaultAccessor(config)
		accessor.configureVault()

		String value = accessor.getValue("path")
		log.log(INFO, "Retrieved from vault: " + value);

		CredentialUpdater updater = new CredentialUpdater(Jenkins.instance)
		updater.updateCredentials("ssh-key", "git", accessor.getValue("sshKey"))

		ConfigJobCreator creator = new ConfigJobCreator()
		creator.generateJobs()
	}
}
