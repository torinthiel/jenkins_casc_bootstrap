package pl.torinthiel.jenkins.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JenkinsApi {
	private static final String API_URL_TEMPLATE = "http://%s:%d/%s";
	private static final String CRUMB_URL_PATH = "crumbIssuer/api/xml?xpath=concat(//crumbRequestField,\":\",//crumb)";
	private static final String TOKEN_URL_PATH = "me/descriptorByName/jenkins.security.ApiTokenProperty/generateNewToken";
	private static final Pattern TOKEN_RESPONSE_PATTERN = Pattern.compile("\"tokenValue\":\"([0-9a-f]+)\"", Pattern.CASE_INSENSITIVE);

	private String containerAddress;
	private int apiPort;

	private String jenkinsUser;
	private String apiToken;

	public JenkinsApi(String containerAddress, int apiPort) {
		this.containerAddress = containerAddress;
		this.apiPort = apiPort;
	}

	public void setupApiToken(String user, String password) throws IOException {
		HttpURLConnection conn = getCrumbConnection(user, password);
		String[] crumb = getStringFromInputStream(conn.getInputStream()).split(":");
		String cookie = conn.getHeaderField("Set-Cookie").split(";")[0];
		conn.disconnect();

		URL tokenUrl = new URL(String.format(API_URL_TEMPLATE, containerAddress, apiPort, TOKEN_URL_PATH));
		conn = (HttpURLConnection) tokenUrl.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Cookie", cookie);
		conn.setRequestProperty(crumb[0], crumb[1]);
		addBasicAuth(user, password, conn);
		String response = getStringFromInputStream(conn.getInputStream());
		conn.disconnect();
		Matcher tokenMatcher = TOKEN_RESPONSE_PATTERN.matcher(response);
		if (!tokenMatcher.find()) {
			throw new IllegalStateException("Cannot find API token in server response");
		}
		this.jenkinsUser = user;
		this.apiToken = tokenMatcher.group(1);
	}

	public HttpURLConnection getCrumbConnection(String user, String password)
			throws MalformedURLException, IOException {
		URL crumbUrl = new URL(String.format(API_URL_TEMPLATE, containerAddress, apiPort, CRUMB_URL_PATH));
		HttpURLConnection conn = (HttpURLConnection) crumbUrl.openConnection();
		addBasicAuth(user, password, conn);
		return conn;
	}

	public String apiCall(String path) throws IOException {
		URL keyUrl = new URL(String.format(API_URL_TEMPLATE, containerAddress, apiPort, path));
		HttpURLConnection conn = (HttpURLConnection) keyUrl.openConnection();
		if (jenkinsUser != null && apiToken != null) {
			addBasicAuth(jenkinsUser, apiToken, conn);
		}
		String result = getStringFromInputStream(conn.getInputStream());
		conn.disconnect();
		return result;
	}

	private void addBasicAuth(String user, String password, HttpURLConnection conn) {
		String credentials = user + ":" + password;
		String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
		conn.setRequestProperty("Authorization", "Basic " + encoded);
	}

	private String getStringFromInputStream(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int bytesRead;
		while ((bytesRead = in.read(buffer, 0, buffer.length)) != -1) {
			out.write(buffer, 0, bytesRead);
		}
		out.flush();
		return new String(out.toByteArray());
	}
}
