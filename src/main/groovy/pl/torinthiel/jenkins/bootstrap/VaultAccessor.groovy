package pl.torinthiel.jenkins.bootstrap

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import static Configs.*
import static java.util.logging.Level.SEVERE
import jenkins.model.Jenkins;

import java.util.function.Function
import java.util.logging.Level
import java.util.logging.Logger

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
	private Logger log = Logger.getLogger(getClass().name)
	private Function<VaultConfig, Vault> vaultFactory
	private Retriever configVars
	private Jenkins jenkins
	private Vault vault
	private Map<String, String> values = new HashMap<>()

	VaultAccessor(Retriever configVars, Jenkins jenkins) {
		this(configVars, new DefaultVaultFactory(), jenkins)
	}

	VaultAccessor(Retriever configVars, Function<VaultConfig, Vault> vaultFactory, Jenkins jenkins) {
		this.configVars = configVars
		this.vaultFactory = vaultFactory
		this.jenkins = jenkins
	}

	void configureVault() {
		String vaultUrl = getOrThrow(VAULT_URL)
		VaultConfig config = new VaultConfig()
			.address(vaultUrl)
			.putSecretsEngineVersionForPath('sys/wrapping/unwrap/', '1')
			.putSecretsEngineVersionForPath('sys/wrapping/lookup/', '1')
			.engineVersion(2)
			.build()

		vault = vaultFactory.apply(config)
		authenticate(config)
		readVariables(config)
	}

	void authenticate(VaultConfig config) {
		maybeAuthenticate(this.&authenticateUser, config, VAULT_USER, VAULT_PW)
		maybeAuthenticate(this.&authenticateAppRole, config, VAULT_APPROLE, VAULT_APPROLE_SECRET)
		maybeAuthenticate(this.&authenticateWrappedAppRole, config, VAULT_APPROLE, VAULT_APPROLE_SECRET_WRAPPED)
		maybeAuthenticate(this.&authenticateToken, config, VAULT_TOKEN)
	}

	void maybeAuthenticate(Closure authMethod, VaultConfig config, Configs... args) {
		def maybeArgs = args.collect{configVars.get(it)}
		if (maybeArgs.every{it.isPresent()}) {
			authMethod(config, *maybeArgs.collect{it.get()})
		}
	}

	private authenticateUser(VaultConfig config, String username, String password) {
		String mount = configVars.get(VAULT_MOUNT).orElse('userpass')
		String token = vault.auth().loginByUserPass(username, password, mount).getAuthClientToken()
		config.token(token).build()
	}

	private authenticateToken(VaultConfig config, String token) {
		config.token(token).build()
	}

	private authenticateAppRole(VaultConfig config, String approle, String secret) {
		String mount = configVars.get(VAULT_MOUNT).orElse('approle')
		String token = vault.auth().loginByAppRole(mount, approle, secret).getAuthClientToken()
		config.token(token).build()
	}

	private authenticateWrappedAppRole(VaultConfig config, String approle, String wrappingToken) {
		configVars.get(VAULT_APPROLE_SECRET_WRAPPED_PATH).ifPresent(this.&validateWrappingTokenPath.curry(wrappingToken))
		config.token(wrappingToken).build()
		String secret = vault.logical().write('sys/wrapping/unwrap', null).data['secret_id']
		authenticateAppRole(config, approle, secret)
	}

	private readVariables(VaultConfig config) {
		def paths = getOrThrow(VAULT_PATHS).split(",")
		paths.collect{vault.logical().read(it).data}.each{current ->
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
		values.containsKey(key.path) ? values[key.path] : key.defaultValue
	}

	private String getOrThrow(Configs configName) {
		configVars.get(configName).orElseThrow({new IllegalArgumentException("CASCB_${configName} not provided")})
	}

	private void validateWrappingTokenPath(String wrappingToken, String expectedPrefix) {
		String path = vault.logical().write('sys/wrapping/lookup', [token: wrappingToken]).data['creation_path']
		if (!path.startsWith(expectedPrefix)) {
			log.log(SEVERE, '#'*80)
			log.log(SEVERE, 'SECURITY ERROR')
			log.log(SEVERE, 'The Hashicorp Vault wrapping token was issued from unexpected path prefix')
			log.log(SEVERE, 'This may indicate misconfiguration, but it may also indicate that')
			log.log(SEVERE, 'the real response was intercepted, read and wrapped again.')
			log.log(SEVERE, 'Because of possible risk the application will be stopped')
			log.log(SEVERE, 'Expected path prefix: ' + expectedPrefix)
			log.log(SEVERE, 'Actual path: ' + path)
			log.log(SEVERE, '#'*80)
			jenkins.doSafeExit()
		}
	}
}

class DefaultVaultFactory implements Function<VaultConfig, Vault> {
	@Override
	public Vault apply(VaultConfig config) {
		new Vault(config);
	}
}
