package pl.torinthiel.jenkins.bootstrap

import java.util.function.Function
import java.util.stream.Stream


enum Configs {
	VAULT_URL,
	VAULT_USER,
	VAULT_PW,
	VAULT_PATHS,
	VAULT_FILE
}

interface Retriever {
	Optional<String> get(Configs configName)
}

class EnvNameMapper {
	private final String prefix

	EnvNameMapper(boolean useBootstrapPrefix) {
		prefix = useBootstrapPrefix ? "CASCB_" : "CASC_"
	}

	String map(Configs configName) {
		return prefix + configName.toString()
	}
}

class EnvRetriever implements Retriever {
	private final EnvNameMapper mapper
	private final Function<String, String> env

	EnvRetriever(EnvNameMapper mapper, Function<String, String> env) {
		this.mapper = mapper
		this.env = env
	}

	public Optional<String> get(Configs configName) {
		String name = mapper.map(configName)
		return Optional.ofNullable(env.apply(name))
	}
}

class FileRetriever implements Retriever {

	private EnvNameMapper mapper
	private Properties values

	FileRetriever(EnvNameMapper mapper, String path) {
		this.mapper = mapper

		values = new Properties()
		InputStream is = new FileInputStream(new File(path))
		try {
			values.load(is)
		} finally {
			is.close()
		}
	}

	Optional<String> get(Configs configName) {
		return Optional.ofNullable(values.getProperty(mapper.map(configName)))
	}
}

class ConfigRetriever implements Retriever {

	private final Retriever[] partials
	private final Function<String, String> env

	ConfigRetriever() {
		this(new DefaultEnvRetriever())
	}

	ConfigRetriever(Function<String, String> env) {
		EnvNameMapper bootstrapMapper = new EnvNameMapper(true)
		EnvNameMapper cascMapper = new EnvNameMapper(false)

		List<Retriever> parts = new ArrayList<>();
		def bootstrapEnv = new EnvRetriever(bootstrapMapper, env)
		def cascEnv = new EnvRetriever(cascMapper, env)
		parts.add(bootstrapEnv)
		fileRetriever(bootstrapEnv, bootstrapMapper).ifPresent({parts.add(it)})
		parts.add(cascEnv)
		fileRetriever(cascEnv, bootstrapMapper).ifPresent({parts.add(it)})
		fileRetriever(cascEnv, cascMapper).ifPresent({parts.add(it)})
		partials = parts.toArray();
	}

	Optional<String> get(Configs configName) {
		return Stream.of(partials).map({it.get(configName)}).filter({it.isPresent()}).findFirst().map({it.get()})
	}

	static private Optional<Retriever> fileRetriever(Retriever pathGetter, EnvNameMapper mapper) {
		Optional<String> maybePath = pathGetter.get(Configs.VAULT_FILE).filter({new File(it).exists()})
		return maybePath.map({new FileRetriever(mapper, it)})
	}
}

class DefaultEnvRetriever implements Function<String, String> {
	@Override
	public String apply(String t) {
		return System.getenv(t)
	}
}
