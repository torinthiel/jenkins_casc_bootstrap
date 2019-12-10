import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import static Configs.*

import java.util.function.Function

class VaultAccessor {
	Vault vault
	ConfigRetriever configVars

	VaultAccessor(ConfigRetriever configVars) {
		this.configVars = configVars
	}

	void configureVault() {
		String vaultUrl = configVars.get(VAULT_URL).orElseThrow({new IllegalArgumentException("CASCB_VAULT_URL not provided")})
		VaultConfig config = new VaultConfig()
			.address(vaultUrl)
			.build()

		vault = new Vault(config)
		authenticate(config)
	}

	void authenticate(VaultConfig config) {
		String user = configVars.get(VAULT_USER).get()
		String pass = configVars.get(VAULT_PW).get()
		String token = vault.auth().loginByUserPass(user, pass, "userpass").getAuthClientToken()
		config.token(token).build()
	}

	String getValue(String key) {
		def data = vault.logical().read("secret/jenkins/config").getData()

		return data.get(key)
	}
}
