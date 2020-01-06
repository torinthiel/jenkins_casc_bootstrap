package pl.torinthiel.jenkins.bootstrap;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Optional;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class SmokeIT extends AbstractIT {
	protected final Logger log = LoggerFactory.getLogger(SmokeIT.class);

	@BeforeEach
	protected void prepareJenkinsContainer() {
		super.prepareJenkinsContainer();

		jenkins
				.withLogConsumer(new Slf4jLogConsumer(log))
				.withEnv("CASCB_VAULT_USER", "jenkins")
				.withEnv("CASCB_VAULT_PW", "S3cRet");
	}

	@Test
	public void shouldInitializeJenkins() throws IOException, DocumentException {
		start();

		apiHelper.setupApiToken("admin", "password");
		assertJobDescription("config", "");
		assertJobPollSchedule("config", "");
		assertCredential("ssh-key", "<description/>");
	}

	@Test
	public void shouldApplyNonDefaultValues() throws IOException, DocumentException {
		// Tests several things at the same time:
		// - that the generated credentials receive values from Vault
		// - that the configuration is taken from correct branch
		// - that the configuration is taken from indicated subdirectories, in order mentioned
		// - that missing directories on the path are skipped
		jenkins.withEnv("CASCB_VAULT_PATHS", "secret/jenkins/config,secret/jenkins/branch_config");

		start();

		apiHelper.setupApiToken("admin", "different_password");
		assertJobDescription("somefolder/subfolder/other_config", "newline\nbackslash '\\' and some \"quotes\"\n");
		assertJobPollSchedule("somefolder/subfolder/other_config", "H H * * *");
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

	private void assertJobDescription(String jobName, String expectedDescription) throws IOException {
		String path = "job/" + jobName.replace("/", "/job/")  + "/description";
		log.info(path);
		String descriptionXml = apiHelper.apiCall(path);
		assertEquals(expectedDescription, descriptionXml);
	}

	private void assertJobPollSchedule(String jobName, String expectedSchedule) throws IOException, DocumentException {
		String path = "job/" + jobName.replace("/", "/job/")  + "/config.xml";
		HttpURLConnection conn = apiHelper.getApiConnection(path);
		Document doc = new SAXReader().read(conn.getInputStream());
		conn.disconnect();
		String scmPollingInterval = Optional.of(doc.getRootElement().element("triggers"))
				.map(triggers -> triggers.element("hudson.triggers.SCMTrigger"))
				.map(scm -> scm.elementText("spec")).orElse("");
		assertEquals(expectedSchedule, scmPollingInterval);
	}
}
