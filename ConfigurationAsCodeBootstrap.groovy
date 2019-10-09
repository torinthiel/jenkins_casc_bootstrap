import java.util.logging.Logger;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.Vault;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.domains.Domain

import static java.util.logging.Level.INFO

public class CascBootstrap {
	Logger log = Logger.getLogger(getClass().name)

	private updateCredentials(String id, String user, String privateKey) {
		def store = Jenkins.instance.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()
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

	public main(String[] ignored) {
		VaultConfig config = new VaultConfig()
			.address("http://172.17.0.3:8200")
			.build();

		Vault vault = new Vault(config);

		// Authenticate
		String token = vault.auth().loginByUserPass("jenkins", "S3cRet", "userpass").getAuthClientToken();
		config.token(token).build();

		def data = vault.logical().read("secret/jenkins/config").getData()

		String value = data.get("path")
		log.log(INFO, "Retrieved from vault: " + value);

		updateCredentials("ssh-key", "git", data.get("sshKey"))
	}
}
