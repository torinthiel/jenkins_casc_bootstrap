package pl.torinthiel.jenkins.bootstrap

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.domains.Domain

import jenkins.model.Jenkins;

class CredentialUpdater {
	Jenkins instance

	CredentialUpdater(Jenkins instance) {
		this.instance = instance
	}

	void updateCredentials(String id, String user, String privateKey) {
		SystemCredentialsProvider plugin = instance.getExtensionList(SystemCredentialsProvider.class)[0]
		def store = plugin.getStore()
		def globalDomain = Domain.global()
		def newKey = new BasicSSHUserPrivateKey(
				CredentialsScope.GLOBAL,
				id,
				user,
				new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(privateKey),
				"",
				"Description"
		)

		def existing = store.getCredentials(globalDomain).find{it.id == "ssh-key"}

		if (existing != null)
			store.updateCredentials(globalDomain, existing, newKey)
		else
			store.addCredentials(globalDomain, newKey)
	}
}
