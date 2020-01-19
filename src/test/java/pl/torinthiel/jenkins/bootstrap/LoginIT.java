package pl.torinthiel.jenkins.bootstrap;

import java.io.IOException;

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
}
