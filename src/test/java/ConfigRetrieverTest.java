import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class ConfigRetrieverTest {

	@Test
	void test() {
		fail("Not yet implemented");
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
