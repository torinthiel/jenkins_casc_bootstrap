package pl.torinthiel.jenkins.bootstrap;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import static java.time.Duration.ofMinutes;
import static org.testcontainers.containers.Network.newNetwork;
import static org.testcontainers.containers.wait.strategy.Wait.forLogMessage;

@Testcontainers
class SmokeIT {
	private static final String DOCKERFILE="itest/Dockerfile";
	private static final int SSH_PORT = 22;
	private static final String VAULT_CONTAINER="vault:1.2.3";
	private static final int VAULT_PORT = 8200;
	private static final int JENKINS_PORT = 8080;

	private static final Network net = newNetwork();
	private static final Path dockerFile = Paths
			.get(Thread.currentThread().getContextClassLoader().getResource(DOCKERFILE).getFile());
	private static final ImageFromDockerfile image = new ImageFromDockerfile().withDockerfile(dockerFile);

	@Container
	private static final GenericContainer<?> gitContainer = new GenericContainer<>(image)
			.withExposedPorts(SSH_PORT)
			.withNetwork(net)
			.withNetworkAliases("git");

	@Container
	private static final ExtendedVaultContainer<?> vaultContainer
			= new ExtendedVaultContainer<>(VAULT_CONTAINER)
			.withAuthEngine("userpass")
			.withPolicy("jenkins_policy", MountableFile.forClasspathResource("itest/jenkins_policy.hcl"))
			.withSecretInVault("auth/userpass/users/jenkins", "password=S3cRet",  "policies=jenkins_policy")
			.withKvAndStdin("secret/jenkins/config",
					MountableFile.forClasspathResource("itest/test_key_rsa"),
					"cascb_ssh_key=-",
					"cascb_repo_url=ssh://git/~/repo")
			.withNetwork(net)
			.withNetworkAliases("vault")
			.withVaultToken("super_secret_root_token")
			.withExposedPorts(VAULT_PORT);

	private final Logger log = LoggerFactory.getLogger(SmokeIT.class);
	private GenericContainer<?> jenkins;

	@BeforeEach
	private void prepareJenkinsContainer() {
		WaitAllStrategy combined = new WaitAllStrategy()
			.withStrategy(forLogMessage(".*Jenkins is fully up and running.*", 1))
			.withStrategy(forLogMessage(".*config #[0-9]+ main build action completed: SUCCESS.*", 1))
			;

		jenkins = new GenericContainer<>("torinthiel/jenkins-bootstrap")
				.withNetwork(net)
				.withLogConsumer(new Slf4jLogConsumer(log))
				.waitingFor(combined)
				.withStartupTimeout(ofMinutes(3))
				.withEnv("CASCB_VAULT_URL", "http://vault:8200/")
				.withEnv("CASCB_VAULT_USER", "jenkins")
				.withEnv("CASCB_VAULT_PW", "S3cRet")
				.withExposedPorts(JENKINS_PORT);
	}

	@Test
	public void shouldInitializeJenkins() throws InterruptedException, IOException {
		jenkins.start();
		assertUserExists("admin", "password");
	}


	private void assertUserExists(String user, String password) throws MalformedURLException, IOException {
		URL crumbUrl = new URL("http://" + jenkins.getContainerIpAddress() + ":" + jenkins.getFirstMappedPort() + "/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)");
		HttpURLConnection conn = (HttpURLConnection) crumbUrl.openConnection();
		addBasicAuth(user, password, conn);
		Assertions.assertEquals(200, conn.getResponseCode(), "The request for crumb ended with error");
	}

	private void addBasicAuth(String user, String password, HttpURLConnection conn) {
		String credentials = user + ":" + password;
		String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
		conn.setRequestProperty("Authorization", "Basic " + encoded);
	}

	@AfterEach
	public void stopJenkinsContainer() {
		if (jenkins != null && jenkins.isRunning()) {
			jenkins.stop();
		}
	}

	@AfterAll
	public static void finalTeardown() {
		net.close();
	}
}
