package pl.torinthiel.jenkins.bootstrap

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain

import jenkins.model.Jenkins;

class CredentialUpdater {
	private Jenkins instance
	private VaultAccessor accessor

	CredentialUpdater(Jenkins instance, VaultAccessor accessor) {
		this.instance = instance
		this.accessor = accessor
	}

	void updateCredentials() {
		String id = accessor.getValue(VaultConfigKey.SSH_ID, 'ssh-key')
		String user = accessor.getValueOrThrow(VaultConfigKey.SSH_USER)
		String privateKey = accessor.getValueOrThrow(VaultConfigKey.SSH_KEY)
		String description = accessor.getValue(VaultConfigKey.SSH_DESCRIPTION, "")
		SystemCredentialsProvider plugin = instance.getExtensionList(SystemCredentialsProvider.class)[0]
		def store = plugin.getStore()
		def globalDomain = Domain.global()
		def newKey = new BasicSSHUserPrivateKey(
				CredentialsScope.GLOBAL,
				id,
				user,
				new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey),
				"",
				description
		)

		def existing = store.getCredentials(globalDomain).find{it.id == id}

		if (existing != null)
			store.updateCredentials(globalDomain, existing, newKey)
		else
			store.addCredentials(globalDomain, newKey)
	}
}
