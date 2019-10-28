import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;

class VaultAccessor {
	Vault vault;

	VaultAccessor() {
		VaultConfig config = new VaultConfig()
		.address("http://172.17.0.3:8200")
		.build();

		vault = new Vault(config);

		// Authenticate
		String token = vault.auth().loginByUserPass("jenkins", "S3cRet", "userpass").getAuthClientToken();
		config.token(token).build();
	}

	String getValue(String key) {
		def data = vault.logical().read("secret/jenkins/config").getData()

		return data.get(key)
	}
}
