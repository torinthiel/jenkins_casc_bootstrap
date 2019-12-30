package pl.torinthiel.jenkins.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class ConfigRetrieverTest {
	Map<String, String> values = new HashMap<>();

	Function<String, String> fakeEnv = new Function<String, String>() {
		@Override
		public String apply(String t) {
			return values.get(t);
		}
	};

	@Test
	void shouldRetrieveFromEnvironment() {
		values.put("CASC_VAULT_USER", "login");
		values.put("CASCB_VAULT_PW", "PaSsWoRd");
		ConfigRetriever retriever = new ConfigRetriever(fakeEnv);

		Optional<String> user = retriever.get(Configs.VAULT_USER);
		Optional<String> pass = retriever.get(Configs.VAULT_PW);
		Optional<String> missing = retriever.get(Configs.VAULT_FILE);

		assertEquals("login", user.get());
		assertEquals("PaSsWoRd", pass.get());
		assertFalse(missing.isPresent());
	}

	@Test
	void shouldRetrieveFromFiles() {
		values.put("CASC_VAULT_FILE", "target/test-classes/ConfigRetriever.properties");
		values.put("CASCB_VAULT_FILE", "target/test-classes/ConfigRetriever2.properties");
		ConfigRetriever retriever = new ConfigRetriever(fakeEnv);

		Optional<String> user = retriever.get(Configs.VAULT_USER);
		Optional<String> pass = retriever.get(Configs.VAULT_PW);
		Optional<String> url = retriever.get(Configs.VAULT_URL);

		assertEquals("login_from_file", user.get());
		assertEquals("PaSsWoRd_from_file", pass.get());
		assertEquals("url_from_file2", url.get());
	}

	@Test
	void shouldNotFailOnMissingFile() {
		values.put("CASC_VAULT_FILE", "target/test-classes/NonexistingFile.properties");
		ConfigRetriever retriever = new ConfigRetriever(fakeEnv);
		assertTrue(retriever.get(Configs.VAULT_FILE).isPresent());
	}

	@Test
	void shouldIgnoreWrongPrefixInFile() {
		values.put("CASCB_VAULT_FILE", "target/test-classes/WrongPrefix.properties");
		ConfigRetriever retriever = new ConfigRetriever(fakeEnv);

		Optional<String> wrongPrefix = retriever.get(Configs.VAULT_USER);

		assertFalse(wrongPrefix.isPresent());
	}

	@Test
	void shouldUseCorrectPrecedence1() {
		values.put("CASC_VAULT_FILE", "target/test-classes/Precedence1a.properties");
		values.put("CASCB_VAULT_FILE", "target/test-classes/Precedence1b.properties");
		values.put("CASCB_VAULT_USER", "user, env, bootstrap");
		values.put("CASC_VAULT_USER", "user, env, casc");
		values.put("CASC_VAULT_PW", "password, env, casc");
		values.put("CASC_VAULT_URL", "url, env, casc");
		ConfigRetriever retriever = new ConfigRetriever(fakeEnv);

		Optional<String> user = retriever.get(Configs.VAULT_USER);
		Optional<String> password = retriever.get(Configs.VAULT_PW);
		Optional<String> url = retriever.get(Configs.VAULT_URL);

		assertEquals("user, env, bootstrap", user.get());
		assertEquals("password, bootfile, bootstrap", password.get());
		assertEquals("url, env, casc", url.get());
	}

	@Test
	void shouldUseCorrectPrecedence2() {
		values.put("CASC_VAULT_FILE", "target/test-classes/Precedence2.properties");
		ConfigRetriever retriever = new ConfigRetriever(fakeEnv);

		Optional<String> user = retriever.get(Configs.VAULT_USER);

		assertEquals("user, cascfile, bootstrap", user.get());
	}
}

class EnvNameMapperTest {

	@Test
	void shouldMapToNameWithBootstrapPrefix() {
		EnvNameMapper mapper = new EnvNameMapper(true);
		String mapped = mapper.map(Configs.VAULT_URL);
		assertEquals("CASCB_VAULT_URL", mapped, "Variable should be mapped with a CASCB_ prefix");
	}

	@Test
	void shouldMapToNameWithUpstreamPrefix() {
		EnvNameMapper mapper = new EnvNameMapper(false);
		String mapped = mapper.map(Configs.VAULT_USER);
		assertEquals("CASC_VAULT_USER", mapped, "Variables should be mapped with upstream CASC_ prefix");
	}
}

class EnvRetrieverTest {
	Map<String, String> values = new HashMap<>();

	Function<String, String> fakeEnv = new Function<String, String>() {
		@Override
		public String apply(String t) {
			return values.get(t);
		}
	};

	@BeforeEach
	void init() {
		values.put("CASCB_VAULT_FILE", "AValue");
		values.put("CASC_VAULT_FILE", "OtherValue");
	}

	@Test
	void shouldRetrieveBootstrapVariable() {
		EnvNameMapper mapper = new EnvNameMapper(true);
		EnvRetriever retriever = new EnvRetriever(mapper, fakeEnv);

		Optional<String> result = retriever.get(Configs.VAULT_FILE);
		assertTrue(result.isPresent(), "The value should be present");
		assertEquals("AValue", result.get(), "The value should be retrieved correctly");
	}

	@Test
	void shouldRetrieveUpstreamVariable() {
		EnvNameMapper mapper = new EnvNameMapper(false);
		EnvRetriever retriever = new EnvRetriever(mapper, fakeEnv);

		Optional<String> result = retriever.get(Configs.VAULT_FILE);
		assertTrue(result.isPresent(), "The value should be present");
		assertEquals("OtherValue", result.get(), "The value should be retrieved correctly");
	}

	@Test
	void shouldRetrieveEmptyWhenNotPresent() {
		EnvNameMapper mapper = new EnvNameMapper(false);
		EnvRetriever retriever = new EnvRetriever(mapper, fakeEnv);

		Optional<String> result = retriever.get(Configs.VAULT_PW);
		assertFalse(result.isPresent(), "The value should not be present");
	}
}

class FileRetrieverTest {
	EnvNameMapper mapper = new EnvNameMapper(true);
	FileRetriever retriever = new FileRetriever(mapper, "target/test-classes/FileRetriever.properties");

	@Test
	void shouldRetrieveBootstrapVariable() {
		Optional<String> result = retriever.get(Configs.VAULT_USER);
		assertTrue(result.isPresent(), "The value should be present");
		assertEquals("UserName", result.get(), "The value should be retrieved correctly");
	}

	@Test
	void shouldRetrieveEmptyWhenNotPresent() {
		Optional<String> result = retriever.get(Configs.VAULT_PW);
		assertFalse(result.isPresent(), "The value should not be present");
	}
}
