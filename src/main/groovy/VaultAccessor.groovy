import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import static Configs.*

import java.util.function.Function

enum VaultConfigKey {
	REPO_URL,
	SSH_KEY

	String getPath() {
		return "cascb_" + toString().toLowerCase()
	}
}

class VaultAccessor {
	Function<VaultConfig, Vault> vaultFactory
	Vault vault
	Retriever configVars

	VaultAccessor(Retriever configVars) {
		this(configVars, new DefaultVaultFactory())
	}

	VaultAccessor(Retriever configVars, Function<VaultConfig, Vault> vaultFactory) {
		this.configVars = configVars
		this.vaultFactory = vaultFactory
	}

	void configureVault() {
		String vaultUrl = configVars.get(VAULT_URL).orElseThrow({new IllegalArgumentException("CASCB_VAULT_URL not provided")})
		VaultConfig config = new VaultConfig()
			.address(vaultUrl)
			.build()

		vault = vaultFactory.apply(config)
		authenticate(config)
	}

	void authenticate(VaultConfig config) {
		String user = configVars.get(VAULT_USER).get()
		String pass = configVars.get(VAULT_PW).get()
		String token = vault.auth().loginByUserPass(user, pass, "userpass").getAuthClientToken()
		config.token(token).build()
	}

	String getValue(VaultConfigKey key) {
		def data = vault.logical().read("secret/jenkins/config").getData()

		return data.get(key.path)
	}
}

class DefaultVaultFactory implements Function<VaultConfig, Vault> {
	@Override
	public Vault apply(VaultConfig config) {
		return new Vault(config);
	}
}
