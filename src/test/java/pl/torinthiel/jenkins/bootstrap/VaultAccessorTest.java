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
		VaultAccessor acc = new VaultAccessor(config, factory);
		acc.configureVault();
	}

	@Test
	void shouldAskForValueAndReturnIt() throws VaultException {
		Map<String, String> resultsMap = new HashMap<>();
		resultsMap.put("cascb_ssh_key", "A_long_ssh_key");
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(resultsMap);

		VaultAccessor acc = new VaultAccessor(config, factory);
		acc.configureVault();

		String retVal = acc.getValue(VaultConfigKey.SSH_KEY);
		assertEquals("A_long_ssh_key", retVal);
	}

	@Test
	void shouldNotAskTwiceForValue() throws VaultException {
		Map<String, String> resultsMap = new HashMap<>();
		resultsMap.put("cascb_ssh_key", "A_long_ssh_key");
		when(vault.logical().read("secret/jenkins/config").getData()).thenReturn(resultsMap);

		VaultAccessor acc = new VaultAccessor(config, factory);
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

		VaultAccessor acc = new VaultAccessor(config, factory);
		acc.configureVault();

		String retVal = acc.getValue(VaultConfigKey.SSH_KEY);
		assertEquals("Correct value", retVal);
	}

	@Test
	void shouldThrowErrorWhenRequiredParamMissing() {
		config.removeMapping(Configs.VAULT_URL);

		Assertions.assertThrows(IllegalArgumentException.class, (Executable) () -> {
			VaultAccessor acc = new VaultAccessor(config, factory);
			acc.configureVault();
			}, "CASCB_VAULT_URL is not provided"
		);
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
