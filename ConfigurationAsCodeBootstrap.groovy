import static java.util.logging.Level.INFO

import java.util.logging.Logger;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain

import jenkins.model.Jenkins;

class VaultAccessor {
	Vault vault;

	VaultAccessor() {
		VaultConfig config = new VaultConfig()
		.address("http://172.17.0.3:8200")
		.build();

		vault = new Vault(config);

		// Authenticate
		String token = vault.auth().loginByUserPass("jenkins", "S3cRet", "userpass").getAuthClientToken();
		config.token(token).build();
	}

	String getValue(String key) {
		def data = vault.logical().read("secret/jenkins/config").getData()

		return data.get(key)
	}
}

class CredentialUpdater {
	Jenkins instance;

	CredentialUpdater(Jenkins instance) {
		this.instance = instance
	}

	void updateCredentials(String id, String user, String privateKey) {
		SystemCredentialsProvider plugin = instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0]
		def store = plugin.getStore()
		def globalDomain = Domain.global()
		def newKey = new BasicSSHUserPrivateKey(
				CredentialsScope.GLOBAL,
				id,
				user,
				new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey),
				"",
				"Description"
		);

		def existing = store.getCredentials(globalDomain).find{it.id =="ssh-key"}

		if (existing != null)
			store.updateCredentials(globalDomain, existing, newKey)
		else
			store.addCredentials(globalDomain, newKey)
	}
}

public class CascBootstrap {
	Logger log = Logger.getLogger(getClass().name)

	public main() {
		VaultAccessor accessor = new VaultAccessor();

		String value = accessor.getValue("path")
		log.log(INFO, "Retrieved from vault: " + value);

		CredentialUpdater updater = new CredentialUpdater(Jenkins.instance)
		updater.updateCredentials("ssh-key", "git", accessor.getValue("sshKey"))
	}
}

new CascBootstrap().main()
