package pl.torinthiel.jenkins.bootstrap;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;

public class JenkinsApi {
	private static final String CRUMB_URL_PATTERN = "http://%s:%d/crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)";

	private String containerAddress;
	private int apiPort;

	public JenkinsApi(String containerAddress, int apiPort) {
		this.containerAddress = containerAddress;
		this.apiPort = apiPort;
	}

	public HttpURLConnection getCrumbConnection(String user, String password)
			throws MalformedURLException, IOException {
		URL crumbUrl = new URL(String.format(CRUMB_URL_PATTERN, containerAddress, apiPort));
		HttpURLConnection conn = (HttpURLConnection) crumbUrl.openConnection();
		addBasicAuth(user, password, conn);
		return conn;
	}

	private void addBasicAuth(String user, String password, HttpURLConnection conn) {
		String credentials = user + ":" + password;
		String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
		conn.setRequestProperty("Authorization", "Basic " + encoded);
	}
}
