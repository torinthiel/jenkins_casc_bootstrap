import java.util.function.Function


enum Configs {
	VAULT_URL,
	VAULT_USER,
	VAULT_PW,
	VAULT_FILE
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

class EnvRetriever {
	private final EnvNameMapper mapper
	private final Function<String, String> env

	EnvRetriever(EnvNameMapper mapper) {
		EnvRetriever(mapper, {System.getenv(it)})
	}

	EnvRetriever(EnvNameMapper mapper, Function<String, String> env) {
		this.mapper = mapper
		this.env = env
	}

	public Optional<String> get(Configs configName) {
		String name = mapper.map(configName)
		return Optional.ofNullable(env.apply(name))
	}
}

class FileRetriever {

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
