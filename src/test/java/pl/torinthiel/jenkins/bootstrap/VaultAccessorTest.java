package pl.torinthiel.jenkins.bootstrap;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class VaultAccessorTest {

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	Vault vault;

	Function<VaultConfig, Vault> factory = config -> vault;

	MockConfigVars config = new MockConfigVars();

	@BeforeEach
	void setUp() {
		config.addMapping(Configs.VAULT_URL, "random_url");
		config.addMapping(Configs.VAULT_USER, "username");
		config.addMapping(Configs.VAULT_PW, "password");
		config.addMapping(Configs.VAULT_PATHS, "secret/jenkins/config");
	}

	@Test
	void shouldCreateAndConfigure() {
		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();
	}

	@Test
	void shouldAskForValueAndReturnIt() throws VaultException {
		Map<String, String> resultsMap = new HashMap<>();
		resultsMap.put("cascb_ssh_key", "A_long_ssh_key");
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(resultsMap);

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		String retVal = acc.getValue(VaultConfigKey.SSH_KEY);
		assertEquals("A_long_ssh_key", retVal);
	}

	@Test
	void shouldNotAskTwiceForValue() throws VaultException {
		Map<String, String> resultsMap = new HashMap<>();
		resultsMap.put("cascb_ssh_key", "A_long_ssh_key");
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(resultsMap);

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		String retVal = acc.getValue(VaultConfigKey.SSH_KEY);
		String retVal2 = acc.getValue(VaultConfigKey.SSH_KEY);
		assertEquals("A_long_ssh_key", retVal);
		assertEquals("A_long_ssh_key", retVal2);
		// Expect one call from when() above and second one from unit under test.
		verify(vault.logical(), times(2)).read("secret/jenkins/config");
	}

	@Test
	void shouldReadVaultPathFromEnv() throws VaultException {
		Map<String, String> errorMap = new HashMap<>();
		errorMap.put("cascb_ssh_key", "Wrong value");
		Map<String, String> resultsMap = new HashMap<>();
		resultsMap.put("cascb_ssh_key", "Correct value");
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(errorMap);
		when(vault.logical().read("secret/jenkins/correct").getData()).thenReturn(resultsMap);
		config.addMapping(Configs.VAULT_PATHS, "secret/jenkins/correct");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		String retVal = acc.getValue(VaultConfigKey.SSH_KEY);
		assertEquals("Correct value", retVal);
	}

	@Test
	void shouldThrowErrorWhenRequiredParamMissing() {
		config.removeMapping(Configs.VAULT_URL);

		Assertions.assertThrows(IllegalArgumentException.class, (Executable) () -> {
				VaultAccessor acc = new VaultAccessor(config, factory, null);
				acc.configureVault();
			}, "CASCB_VAULT_URL is not provided"
		);
	}

	@Test
	void shouldReadFromMultiplePaths() throws VaultException {
		Map<String, String> firstMap = new HashMap<>();
		firstMap.put("cascb_ssh_key", "First value");
		Map<String, String> secondMap = new HashMap<>();
		secondMap.put("cascb_repo_url", "Second value");
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(firstMap);
		when(vault.logical().read("secret/jenkins/supplement").getData()).thenReturn(secondMap);
		config.addMapping(Configs.VAULT_PATHS, "secret/jenkins/config,secret/jenkins/supplement");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		String firstVal = acc.getValue(VaultConfigKey.SSH_KEY);
		assertEquals("First value", firstVal);
		String secondVal = acc.getValue(VaultConfigKey.REPO_URL);
		assertEquals("Second value", secondVal);
	}

	@Test
	void ifValueIsProvidedInTwoPathsLatterTakesPrecedence() throws VaultException {
		Map<String, String> firstMap = new HashMap<>();
		firstMap.put("cascb_ssh_key", "First value");
		firstMap.put("cascb_ssh_user", "Other value");
		Map<String, String> secondMap = new HashMap<>();
		secondMap.put("cascb_ssh_key", "Second value");
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(firstMap);
		when(vault.logical().read("secret/jenkins/supplement").getData()).thenReturn(secondMap);
		config.addMapping(Configs.VAULT_PATHS, "secret/jenkins/config,secret/jenkins/supplement");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		String firstVal = acc.getValue(VaultConfigKey.SSH_KEY);
		assertEquals("Second value", firstVal);
		String secondVal = acc.getValue(VaultConfigKey.SSH_USER);
		assertEquals("Other value", secondVal);
	}

	@Test
	void shouldThrowErrorIfRequiredSettingMissing() throws VaultException {
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(new HashMap<>());

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		Assertions.assertThrows(IllegalArgumentException.class, (Executable) () -> {
				acc.getValue(VaultConfigKey.REPO_URL);
			}, "cascb_repo_url is not available in vault"
		);
	}

	@Test
	void shouldReturnDefaultValueIfMissing() throws VaultException {
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(new HashMap<>());

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		String value = acc.getValue(VaultConfigKey.SSH_ID);
		assertEquals("ssh-key", value);
	}

	@Test
	void shouldCombineValueWithPrevious() throws VaultException {
		Map<String, String> skippedMap = new HashMap<>();
		skippedMap.put("cascb_repo_directories", "ignored_value");
		Map<String, String> firstMap = new HashMap<>();
		firstMap.put("cascb_repo_directories", "first_value");
		Map<String, String> secondMap = new HashMap<>();
		secondMap.put("cascb_repo_directories", "(+),second_value");
		when(vault.logical().read("secret/jenkins/skipped").getData()).thenReturn(skippedMap);
		when(vault.logical().read("secret/jenkins/first").getData()).thenReturn(firstMap);
		when(vault.logical().read("secret/jenkins/missing").getData()).thenReturn(new HashMap<>());
		when(vault.logical().read("secret/jenkins/second").getData()).thenReturn(secondMap);
		config.addMapping(Configs.VAULT_PATHS, "secret/jenkins/skipped,secret/jenkins/first,secret/jenkins/missing,secret/jenkins/second");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		String value = acc.getValue(VaultConfigKey.REPO_DIRECTORIES);
		assertEquals("first_value,second_value", value);
	}

	@Test
	void shouldIgnoreMeergeMarkerOnFirst() throws VaultException {
		Map<String, String> resultsMap = new HashMap<>();
		resultsMap.put("cascb_repo_directories", "(+),correct_value");
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(resultsMap);

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		String retVal = acc.getValue(VaultConfigKey.REPO_DIRECTORIES);
		assertEquals("correct_value", retVal);
	}
}

@ExtendWith(MockitoExtension.class)
class VaultAccessorLoginTest {

	@Mock(answer = Answers.RETURNS_DEEP_STUBS)
	Vault vault;

	VaultConfig usedConfig;
	Function<VaultConfig, Vault> factory = config -> {usedConfig = config; return vault;};

	MockConfigVars config = new MockConfigVars();

	@BeforeEach
	void setUp() {
		config.addMapping(Configs.VAULT_URL, "random_url");
		config.addMapping(Configs.VAULT_PATHS, "secret/jenkins/config");
	}

	@Test
	void shouldLogInAsUserIfPossible() throws VaultException {
		config.addMapping(Configs.VAULT_USER, "username");
		config.addMapping(Configs.VAULT_PW, "password");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		verify(vault.auth()).loginByUserPass(eq("username"), eq("password"), anyString());
	}

	@Test
	void shouldLogInAsUserWithPathIfPossible() throws VaultException {
		config.addMapping(Configs.VAULT_USER, "username");
		config.addMapping(Configs.VAULT_PW, "password");
		config.addMapping(Configs.VAULT_MOUNT, "somepath");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		verify(vault.auth()).loginByUserPass("username", "password", "somepath");
	}

	@Test
	void shouldNotLogInAsUserIfUserMissing() throws VaultException {
		config.addMapping(Configs.VAULT_PW, "password");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		verify(vault.auth(), times(0)).loginByUserPass(anyString(), anyString(), anyString());
	}

	@Test
	void shouldNotLogInAsUserIfPasswordMissing() throws VaultException {
		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		verify(vault.auth(), times(0)).loginByUserPass(anyString(), anyString(), anyString());
	}

	@Test
	void shouldUseTokenIfProvided() {
		String token = "some_would_be_token";
		config.addMapping(Configs.VAULT_TOKEN, token);

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		assertNotNull(usedConfig);
		assertEquals(token, usedConfig.getToken());
	}

	@Test
	void shouldLogInAsAppRoleIfPossible() throws VaultException {
		config.addMapping(Configs.VAULT_APPROLE, "approle");
		config.addMapping(Configs.VAULT_APPROLE_SECRET, "secret");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		verify(vault.auth()).loginByAppRole(anyString(), eq("approle"), eq("secret"));
	}

	@Test
	void shouldLogInAsAppRoleWithPathIfPossible() throws VaultException {
		config.addMapping(Configs.VAULT_APPROLE, "approle");
		config.addMapping(Configs.VAULT_APPROLE_SECRET, "secret");
		config.addMapping(Configs.VAULT_MOUNT, "otherpath");

		VaultAccessor acc = new VaultAccessor(config, factory, null);
		acc.configureVault();

		verify(vault.auth()).loginByAppRole("otherpath", "approle", "secret");
	}
}

class MockConfigVars implements Retriever {
	Map<Configs, String> config = new EnumMap<>(Configs.class);

	@Override
	public Optional<String> get(Configs configName) {
		return Optional.ofNullable(config.get(configName));
	}

	public void addMapping(Configs key, String value) {
		config.put(key, value);
	}

	public void removeMapping(Configs key) {
		config.remove(key);
	}
}
