package pl.torinthiel.jenkins.bootstrap

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import static Configs.*

import java.util.function.Function

enum VaultConfigKey {
	SSH_USER,
	SSH_DESCRIPTION(''),
	SSH_ID('ssh-key'),
	SSH_KEY,
	REPO_URL,
	REPO_BRANCH('master'),
	REPO_DIRECTORIES('.'),
	JOB_NAME('config'),
	JOB_DESCRIPTION(''),
	JOB_POLL_SCHEDULE('')

	final Optional<String> defaultValue

	VaultConfigKey() {
		this(null)
	}

	VaultConfigKey(String defaultValue) {
		this.defaultValue = Optional.ofNullable(defaultValue)
	}

	String getPath() {
		return "cascb_" + toString().toLowerCase()
	}

	String getDefaultValue() {
		return defaultValue.orElseThrow({->new IllegalArgumentException("cascb_${this.toString().toLowerCase()} not available in vault")})
	}

	private static final MERGABLE_KEYS = EnumSet.of(REPO_DIRECTORIES)*.path
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
		maybeAuthenticate(this.&authenticateUser, config, VAULT_USER, VAULT_PW)
		maybeAuthenticate(this.&authenticateAppRole, config, VAULT_APPROLE, VAULT_APPROLE_SECRET)
		maybeAuthenticate(this.&authenticateToken, config, VAULT_TOKEN)
	}

	void maybeAuthenticate(Closure authMethod, VaultConfig config, Configs... args) {
		def maybeArgs = args.collect{configVars.get(it)}
		if (maybeArgs.every{it.isPresent()}) {
			authMethod(config, *maybeArgs.collect{it.get()})
		}
	}

	private authenticateUser(VaultConfig config, String username, String password) {
		String token = vault.auth().loginByUserPass(username, password, "userpass").getAuthClientToken()
		config.token(token).build()
	}

	private authenticateToken(VaultConfig config, String token) {
		config.token(token).build()
	}

	private authenticateAppRole(VaultConfig config, String approle, String secret) {
		String token = vault.auth().loginByAppRole("approle", approle, secret).getAuthClientToken()
		config.token(token).build()
	}

	void readVariables(VaultConfig config) {
		def paths = getOrThrow(VAULT_PATHS).split(",")
		paths.collect{vault.logical().read(it).getData()}.each{current ->
			current.each{key, newValue ->
				values.compute(key, {_, prevValue ->
					if (newValue && newValue.startsWith('(+),') && key in VaultConfigKey.MERGABLE_KEYS) {
						if (prevValue) {
							prevValue + newValue.replace('(+)', '')
						} else {
							newValue.replace('(+),', '')
						}
					} else {
						newValue ?: prevValue
					}
				})
			}
		}
	}

	String getValue(VaultConfigKey key) {
		values.containsKey(key.path) ? values.get(key.path) : key.defaultValue
	}

	private String getOrThrow(Configs configName) {
		configVars.get(configName).orElseThrow({new IllegalArgumentException("CASCB_${configName} not provided")})
	}
}

class DefaultVaultFactory implements Function<VaultConfig, Vault> {
	@Override
	public Vault apply(VaultConfig config) {
		new Vault(config);
	}
}
