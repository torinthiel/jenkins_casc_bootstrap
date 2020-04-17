package pl.torinthiel.jenkins.bootstrap;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.MountableFile;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;

import static java.time.Duration.ofMinutes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testcontainers.containers.Network.newNetwork;
import static org.testcontainers.containers.wait.strategy.Wait.forLogMessage;

public class AbstractIT {

	private static final String DOCKERFILE = "itest/Dockerfile";
	private static final int SSH_PORT = 22;
	private static final String VAULT_CONTAINER = "vault:1.4.0";
	private static final int VAULT_PORT = 8200;
	protected static final int JENKINS_PORT = 8080;

	protected static final Network net = newNetwork();

	private static final Path dockerFile = Paths
				.get(Thread.currentThread().getContextClassLoader().getResource(DOCKERFILE).getFile());
	private static final ImageFromDockerfile image = new ImageFromDockerfile().withDockerfile(dockerFile);
	@Container
	private static final GenericContainer<?> gitContainer = new GenericContainer<>(image)
				.withExposedPorts(SSH_PORT)
				.withNetwork(net)
				.withNetworkAliases("git");

	@Container
	private static final ExtendedVaultContainer<?> vaultContainer = new ExtendedVaultContainer<>(VAULT_CONTAINER)
			.withAuthEngine("userpass")
			.withAuthEngine("approle")
			.withAuthEngine("userpass", "otherusermount")
			.withAuthEngine("approle", "otherrolemount")
			.withPolicy("jenkins_policy", MountableFile.forClasspathResource("itest/jenkins_policy.hcl"))
			.withSecretInVault("auth/userpass/users/jenkins", "password=S3cRet",  "policies=jenkins_policy")
			.withSecretInVault2("auth/approle/role/jenkins_role", "token_policies=jenkins_policy")
			.withSecretInVault("auth/approle/role/jenkins_role/role-id", "role_id=custom_approle_id")
			.withSecretInVault("auth/approle/role/jenkins_role/custom-secret-id", "secret_id=custom_approle_secret")
			.withSecretInVault("auth/otherusermount/users/mountuser", "password=mountpassword",  "policies=jenkins_policy")
			.withSecretInVault2("auth/otherrolemount/role/jenkins_role", "token_policies=jenkins_policy")
			.withSecretInVault("auth/otherrolemount/role/jenkins_role/role-id", "role_id=different_approle_id")
			.withSecretInVault("auth/otherrolemount/role/jenkins_role/custom-secret-id", "secret_id=different_approle_secret")
			.withKvAndStdin("secret/jenkins/config",
					MountableFile.forClasspathResource("itest/test_key_rsa"),
					"cascb_ssh_key=-",
					"cascb_ssh_user=git",
					"cascb_repo_url=ssh://git/~/repo")
			.withKvAndStdin("secret/jenkins/branch_config",
					MountableFile.forClasspathResource("itest/job_description"),
					"cascb_job_name=somefolder/subfolder/other_config",
					"cascb_job_description=-",
					"cascb_job_poll_schedule=H H * * *",
					"cascb_repo_branch=other_branch",
					"cascb_repo_directories=zz_first,subdir,nonexistent",
					"cascb_ssh_id=foobar",
					"cascb_ssh_description=Testable description")
			.withNetwork(net)
			.withNetworkAliases("vault")
			.withVaultToken("super_secret_root_token")
			.withExposedPorts(VAULT_PORT);

	protected GenericContainer<?> jenkins;
	protected JenkinsApi apiHelper;

	@BeforeEach
	protected void prepareJenkinsContainer() {
		WaitAllStrategy combined = new WaitAllStrategy()
			.withStrategy(forLogMessage(".*Jenkins is fully up and running.*", 1))
			.withStrategy(forLogMessage(".*Configuration job finished successfully\n", 1))
			;

		jenkins = new GenericContainer<>(new ImageFromDockerfile().withDockerfile(Paths.get("Dockerfile").toAbsolutePath()))
				.withNetwork(net)
				.waitingFor(combined)
				.withStartupTimeout(ofMinutes(3))
				.withEnv("CASCB_VAULT_URL", "http://vault:8200/")
				.withEnv("CASCB_VAULT_PATHS", "secret/jenkins/config")
				.withExposedPorts(JENKINS_PORT);
	}

	@AfterEach
	public void stopJenkinsContainer() {
		if (jenkins != null && jenkins.isRunning()) {
			jenkins.stop();
		}
	}

	protected void start() {
		jenkins.start();
		apiHelper = new JenkinsApi(jenkins.getContainerIpAddress(), jenkins.getFirstMappedPort());
	}

	protected void assertUserExists(String user, String password) throws IOException {
		HttpURLConnection conn = apiHelper.getCrumbConnection(user, password);
		assertEquals(200, conn.getResponseCode(), "The request for crumb ended with error");
		conn.disconnect();
	}

	protected String runCommandInVault(String... command) throws UnsupportedOperationException, IOException, InterruptedException {
		// This code is mostly copied from org.testcontainers.containers.ExecInContainerPattern.execInContainer
		// The original uses org.testcontainers.containers.output.ToStringConsumer which behaves weird
		// - it inserts a newline every frame, but
		// a) the input already contains newlines
		// b) frame boundaries appear in random places
		// Thus the result is full of unneeded newlines, and a few needed ones that cannot be distinguished.
		DockerClient dockerClient = DockerClientFactory.instance().client();
		String containerId = vaultContainer.getContainerId();
		final ExecCreateCmdResponse execCreateCmdResponse =
				dockerClient.execCreateCmd(containerId).withAttachStdout(true).withCmd(command).exec();
		final ToStringConsumer stdoutConsumer = new ToStringConsumer();
		FrameConsumerResultCallback callback = new FrameConsumerResultCallback();
		callback.addConsumer(OutputFrame.OutputType.STDOUT, stdoutConsumer);
		dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(callback).awaitCompletion();
		return stdoutConsumer.toUtf8String();
	}
}
