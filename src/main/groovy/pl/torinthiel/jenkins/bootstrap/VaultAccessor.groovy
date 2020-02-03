package pl.torinthiel.jenkins.bootstrap

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import static Configs.*

import java.util.function.Function

enum VaultConfigKey {
	REPO_URL,
	REPO_BRANCH,
	SSH_KEY

	String getPath() {
		return "cascb_" + toString().toLowerCase()
	}
}

class VaultAccessor {
	private Function<VaultConfig, Vault> vaultFactory
	private Vault vault
	private Retriever configVars
	private Map<String, String> values = new HashMap<>()

	VaultAccessor(Retriever configVars) {
		this(configVars, new DefaultVaultFactory())
	}

	VaultAccessor(Retriever configVars, Function<VaultConfig, Vault> vaultFactory) {
		this.configVars = configVars
		this.vaultFactory = vaultFactory
	}

	void configureVault() {
		String vaultUrl = getOrThrow(VAULT_URL)
		VaultConfig config = new VaultConfig()
			.address(vaultUrl)
			.build()

		vault = vaultFactory.apply(config)
		authenticate(config)
		readVariables(config)
	}

	void authenticate(VaultConfig config) {
		String user = configVars.get(VAULT_USER).get()
		String pass = configVars.get(VAULT_PW).get()
		String token = vault.auth().loginByUserPass(user, pass, "userpass").getAuthClientToken()
		config.token(token).build()
	}

	void readVariables(VaultConfig config) {
		def paths = getOrThrow(VAULT_PATHS).split(",")
		paths.collect{vault.logical().read(it).getData()}.each(values.&putAll)
	}

	private String getOrThrow(Configs configName) {
		configVars.get(configName).orElseThrow({new IllegalArgumentException("CASCB_${configName} not provided")})
	}

	String getValue(VaultConfigKey key) {
		values.get(key.path)
	}
}

class DefaultVaultFactory implements Function<VaultConfig, Vault> {
	@Override
	public Vault apply(VaultConfig config) {
		new Vault(config);
	}
}
