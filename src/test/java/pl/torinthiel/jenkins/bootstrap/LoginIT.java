package pl.torinthiel.jenkins.bootstrap;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.dom4j.DocumentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class LoginIT extends AbstractIT {
	protected final Logger log = LoggerFactory.getLogger(LoginIT.class);

	@BeforeEach
	protected void prepareJenkinsContainer() {
		super.prepareJenkinsContainer();

		jenkins.withLogConsumer(new Slf4jLogConsumer(log));
	}

	@Test
	public void shouldLoginViaToken() throws IOException, DocumentException {
		jenkins.withEnv("CASCB_VAULT_TOKEN", "super_secret_root_token");

		start();

		assertUserExists("admin", "password");
	}

	@Test
	public void shouldLoginViaAppRole() throws IOException, DocumentException {
		jenkins.withEnv("CASCB_VAULT_APPROLE", "custom_approle_id");
		jenkins.withEnv("CASCB_VAULT_APPROLE_SECRET", "custom_approle_secret");

		start();

		assertUserExists("admin", "password");
	}

	@Test
	public void shouldLoginViaUserPassMount() throws IOException, DocumentException {
		jenkins.withEnv("CASC_VAULT_USER", "mountuser");
		jenkins.withEnv("CASC_VAULT_PW", "mountpassword");
		jenkins.withEnv("CASCB_VAULT_MOUNT", "otherusermount");

		start();

		assertUserExists("admin", "password");
	}

	@Test
	public void shouldLoginViaAppRoleMount() throws IOException, DocumentException {
		jenkins.withEnv("CASCB_VAULT_APPROLE", "different_approle_id");
		jenkins.withEnv("CASCB_VAULT_APPROLE_SECRET", "different_approle_secret");
		jenkins.withEnv("CASCB_VAULT_MOUNT", "otherrolemount");

		start();

		assertUserExists("admin", "password");
	}

	@Test
	public void shouldLoginViaAppRoleWrapped() throws Exception {
		String result = runCommandInVault("vault", "write", "-force", "-wrap-ttl=10m", "auth/approle/role/jenkins_role/secret-id");
		String wrappingToken = extractFieldFromResponse("wrapping_token", result);

		jenkins.withEnv("CASCB_VAULT_APPROLE", "custom_approle_id");
		jenkins.withEnv("CASCB_VAULT_APPROLE_SECRET_WRAPPED", wrappingToken);

		start();

		assertUserExists("admin", "password");
	}

	private String extractFieldFromResponse(String field, String response) {
		String[] lines = response.split("\\R");
		Pattern expr = Pattern.compile("^"+field+"\\b");

		return Arrays
				.stream(lines)
				.filter(s -> expr.matcher(s).find())
				.map(StringUtils::split)
				.map(arr -> arr[arr.length-1])
				.findFirst()
				.get();
	}
}
