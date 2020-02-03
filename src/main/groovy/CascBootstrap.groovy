import static java.util.logging.Level.INFO

import java.util.logging.Logger;
import jenkins.model.Jenkins;

public class CascBootstrap {
	Logger log = Logger.getLogger(getClass().name)

	public main() {
		def config = new ConfigRetriever()

		VaultAccessor accessor = new VaultAccessor(config)
		accessor.configureVault()
		log.log(INFO, "Connected to vault")

		CredentialUpdater updater = new CredentialUpdater(Jenkins.instance)
		updater.updateCredentials("ssh-key", "git", accessor.getValue(VaultConfigKey.SSH_KEY))
		log.log(INFO, "Configured credentials")

		ConfigJobCreator creator = new ConfigJobCreator(accessor)
		creator.generateJobs()
		log.log(INFO, "Created configuration job")
	}
}
