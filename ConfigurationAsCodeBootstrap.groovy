import java.util.logging.Logger;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.Vault;

import static java.util.logging.Level.INFO

public class CascBootstrap {
	Logger log = Logger.getLogger(getClass().name)

	public main(String[] ignored) {
		VaultConfig config = new VaultConfig()
			.address("http://172.17.0.3:8200")
			.build();

		Vault vault = new Vault(config);

		// Authenticate
		String token = vault.auth().loginByUserPass("jenkins", "S3cRet", "userpass").getAuthClientToken();
		config.token(token).build();

		String value = vault.logical().read("secret/jenkins/config").getData().get("path")
		log.log(INFO, "Retrieved from vault: " + value);
	}
}
