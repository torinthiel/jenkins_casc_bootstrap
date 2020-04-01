package pl.torinthiel.jenkins.bootstrap

import java.util.function.Function


enum Configs {
	VAULT_URL,
	VAULT_PATHS,
	VAULT_USER,
	VAULT_PW,
	VAULT_TOKEN,
	VAULT_APPROLE,
	VAULT_APPROLE_SECRET,
	VAULT_APPROLE_SECRET_WRAPPED,
	VAULT_MOUNT,
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
		prefix + configName.toString()
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
		Optional.ofNullable(values[mapper.map(configName)])
	}
}

class ConfigRetriever implements Retriever {

	private final List<Retriever> partials
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
		fileRetriever(bootstrapEnv, bootstrapMapper).ifPresent(parts.&add)
		parts.add(cascEnv)
		fileRetriever(cascEnv, bootstrapMapper).ifPresent(parts.&add)
		fileRetriever(cascEnv, cascMapper).ifPresent(parts.&add)
		partials = parts
	}

	Optional<String> get(Configs configName) {
		partials.collect{it.get(configName)}.find{it.isPresent()} ?: Optional.empty()
	}

	static private Optional<Retriever> fileRetriever(Retriever pathGetter, EnvNameMapper mapper) {
		pathGetter.get(Configs.VAULT_FILE).filter{new File(it).exists()}.map{new FileRetriever(mapper, it)}
	}
}

class DefaultEnvRetriever implements Function<String, String> {
	@Override
	public String apply(String t) {
		System.getenv(t)
	}
}
