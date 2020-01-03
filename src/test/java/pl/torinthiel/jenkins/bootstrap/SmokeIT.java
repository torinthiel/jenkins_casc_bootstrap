package pl.torinthiel.jenkins.bootstrap;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
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
					"cascb_ssh_user=git",
					"cascb_repo_url=ssh://git/~/repo")
			.withKvAndStdin("secret/jenkins/branch_config",
					MountableFile.forClasspathResource("itest/job_description"),
					"cascb_job_name=somefolder/subfolder/other_config",
					"cascb_job_description=-",
					"cascb_repo_branch=other_branch",
					"cascb_repo_directories=zz_first,subdir,nonexistent",
					"cascb_ssh_id=foobar",
					"cascb_ssh_description=Testable description")
			.withNetwork(net)
			.withNetworkAliases("vault")
			.withVaultToken("super_secret_root_token")
			.withExposedPorts(VAULT_PORT);

	private final Logger log = LoggerFactory.getLogger(SmokeIT.class);
	private GenericContainer<?> jenkins;
	private JenkinsApi apiHelper;

	@BeforeEach
	private void prepareJenkinsContainer() {
		WaitAllStrategy combined = new WaitAllStrategy()
			.withStrategy(forLogMessage(".*Jenkins is fully up and running.*", 1))
			// Accept any job with 'config' in name
			.withStrategy(forLogMessage(".*config[a-zA-Z]* #[0-9]+ main build action completed: SUCCESS.*", 1))
			;

		jenkins = new GenericContainer<>(new ImageFromDockerfile().withDockerfile(Paths.get("Dockerfile").toAbsolutePath()))
				.withNetwork(net)
				.withLogConsumer(new Slf4jLogConsumer(log))
				.waitingFor(combined)
				.withStartupTimeout(ofMinutes(3))
				.withEnv("CASCB_VAULT_URL", "http://vault:8200/")
				.withEnv("CASCB_VAULT_USER", "jenkins")
				.withEnv("CASCB_VAULT_PW", "S3cRet")
				.withEnv("CASCB_VAULT_PATHS", "secret/jenkins/config")
				.withExposedPorts(JENKINS_PORT);
	}

	@Test
	public void shouldInitializeJenkins() throws IOException {
		start();

		apiHelper.setupApiToken("admin", "password");
		assertJobDescription("config", "<description/>");
		assertCredential("ssh-key", "<description/>");
	}

	@Test
	public void shouldApplyNonDefaultValues() throws IOException {
		// Tests several things at the same time:
		// - that the generated credentials receive values from Vault
		// - that the configuration is taken from correct branch
		// - that the configuration is taken from indicated subdirectories, in order mentioned
		// - that missing directories on the path are skipped
		jenkins.withEnv("CASCB_VAULT_PATHS", "secret/jenkins/config,secret/jenkins/branch_config");

		start();

		apiHelper.setupApiToken("admin", "different_password");
		assertJobDescription("somefolder/subfolder/other_config", "<description>newline\nbackslash '\\' and some \"quotes\"\n</description>");
		assertCredential("foobar", "<description>Testable description</description>");
		assertUserExists("second_user", "other_password");
	}

	private void assertCredential(String id, String expectedDescription) throws IOException {
		// Actually asserts 4 properties of the credential:
		// - username and actual key, as the setup has succeeded
		// - id, as it's part of the path
		// - description, explicitly

		String path = String.format("credentials/store/system/domain/_/credential/%s/api/xml?xpath=//description", id);
		String descriptionXml = apiHelper.apiCall(path);
		assertEquals(expectedDescription, descriptionXml);
	}

	private void start() {
		jenkins.start();
		apiHelper = new JenkinsApi(jenkins.getContainerIpAddress(), jenkins.getFirstMappedPort());
	}

	private void assertUserExists(String user, String password) throws IOException {
		HttpURLConnection conn = apiHelper.getCrumbConnection(user, password);
		assertEquals(200, conn.getResponseCode(), "The request for crumb ended with error");
		conn.disconnect();
	}

	private void assertJobDescription(String jobName, String expectedDescription) throws IOException {
		String path = "job/" + jobName.replace("/", "/job/")  + "/api/xml?xpath=*/description";
		log.info(path);
		String descriptionXml = apiHelper.apiCall(path);
		assertEquals(expectedDescription, descriptionXml);
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
