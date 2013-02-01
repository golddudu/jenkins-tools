package com.golddudu;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;

/**
 * java jenkins api using json with authentication
 * 
 * @author DuduG
 * 
 */
public class JenkinsApi {

	private DefaultHttpClient client;
	private BasicHttpContext context;
	private String API = "/api/json";

	private String jenkinsUrl;
	private String username;
	private String password;
	private String buildToken;

	/**
	 * 
	 * @param host
	 * @param port
	 * @param username
	 * @param password
	 * @param buildToken
	 */
	public JenkinsApi(String host, String port, String username, String password, String buildToken) {

		jenkinsUrl = "http://" + host + ":" + port;
		setUsername(username);
		setPassword(password);
		setBuildToken(buildToken);

		initClient();

	}

	/**
	 * create and init the client for your jenkins host with configured credentials
	 */
	public void initClient() {
		// Create your httpclient
		client = new DefaultHttpClient();

		// Then provide the right credentials
		client.getCredentialsProvider().setCredentials(new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
				new UsernamePasswordCredentials(username, password));

		// Generate BASIC scheme object and stick it to the execution context
		BasicScheme basicAuth = new BasicScheme();
		context = new BasicHttpContext();
		context.setAttribute("preemptive-auth", basicAuth);

		// Add as the first (because of the zero) request interceptor
		// It will first intercept the request and preemptively initialize the authentication scheme if there is not
		client.addRequestInterceptor(new PreemptiveAuth(), 0);
	}

	// /**
	// * test only!!!
	// *
	// * @param args
	// * @throws Exception
	// */
	// public static void main(String[] args) throws Exception {
	//
	// JenkinsApi jenks = new JenkinsApi("localhost", "8080", "user", "pass", "1111222233334444");
	//
	// String jobName = "job";
	//
	// HashMap<String, String> params = new HashMap<String, String>() {
	// {
	// put("p1", "one");
	// put("p2", "two");
	// }
	// };
	//
	// jenks.buildWithParamAndWaitTillFinished(jobName, params);
	//
	// }

	/**
	 * build job with params wait till finished and return if success
	 * 
	 * @param jobName
	 * @param params
	 * @return the build result status true for success
	 * @throws Exception
	 */
	public boolean buildWithParamAndWaitTillFinished(String jobName, HashMap<String, String> params) throws Exception {

		int lastBuildNumber = getLastBuildNumber(jobName);

		buildWithParam(jobName, params);

		waitForBuildToStart(jobName, lastBuildNumber + 1);

		waitForBuildToFinish(jobName, lastBuildNumber + 1, 20);

		return isBuildSuccess(jobName, lastBuildNumber + 1);

	}

	/**
	 * checks if build result is success
	 * 
	 * @param jobName
	 * @param buildNumber
	 * @return
	 * @throws Exception
	 */
	public boolean isBuildSuccess(String jobName, int buildNumber) throws Exception {
		String getUrl = jenkinsUrl + "/job/" + jobName + "/" + buildNumber + API;
		JSONObject responseJson;
		String resultStr;
		try {
			responseJson = runHttp(client, context, getUrl);
			resultStr = responseJson.getString("result");
		} catch (Exception e) {
			return false;
		}

		System.out.println("Build finised with : " + resultStr + "[" + resultStr.equals("SUCCESS") + "]");

		return resultStr.equals("SUCCESS");
	}

	/**
	 * only for non params builds
	 * 
	 * @param jobName
	 * @return
	 * @throws JSONException
	 */
	public boolean buildJob(String jobName) throws JSONException {

		String getUrl = jenkinsUrl + "/job/" + jobName + "/build?token=" + buildToken;

		runHttp(client, context, getUrl);

		return false;

	}

	/**
	 * only for parametrized builds
	 * 
	 * @param jobName
	 * @param params
	 * @throws JSONException
	 */
	public void buildWithParam(String jobName, Map<String, String> params) {

		String query = "";
		for (Entry<String, String> param : params.entrySet()) {
			query = query + param.getKey() + "=" + param.getValue() + "&";
		}
		query = query.substring(0, query.lastIndexOf('&'));

		// You get request that will start the build
		String getUrl = jenkinsUrl + "/job/" + jobName + "/buildWithParameters?" + query + "&token=" + buildToken;

		try {
			runHttp(client, context, getUrl);
		} catch (java.lang.IllegalArgumentException e) {
			System.out.println(" Problem in parameter " + e.getMessage());
		}

	}

	/**
	 * get latest build number
	 * 
	 * @param jobName
	 * @return
	 * @throws JSONException
	 */
	public int getLastBuildNumber(String jobName) throws JSONException {

		String getUrl = jenkinsUrl + "/job/" + jobName + "/lastBuild" + API;

		JSONObject responseJson = runHttp(client, context, getUrl);

		return responseJson.getInt("number");
	}

	/**
	 * check if build stated
	 * 
	 * @param jobName
	 * @param buildNumber
	 * @return
	 * @throws JSONException
	 */
	public boolean isBuildStarted(String jobName, int buildNumber) throws JSONException {

		String getUrl = jenkinsUrl + "/job/" + jobName + "/" + buildNumber + API;
		JSONObject responseJson;
		responseJson = runHttp(client, context, getUrl);
		if (responseJson == null) {
			return false;
		}

		String buildingStr;
		try {
			buildingStr = responseJson.getString("building");
		} catch (NullPointerException e) {
			return false;
		}
		boolean isBuilding = Boolean.parseBoolean(buildingStr);
		boolean isFinished = isBuildFinished(jobName, buildNumber);

		return (isBuilding || isFinished);
	}

	/**
	 * check if build finished
	 * 
	 * @param jobName
	 * @param buildNumber
	 * @return
	 * @throws JSONException
	 */
	public boolean isBuildFinished(String jobName, int buildNumber) throws JSONException {

		String getUrl = jenkinsUrl + "/job/" + jobName + "/" + buildNumber + API;
		JSONObject responseJson;
		responseJson = runHttp(client, context, getUrl);
		if (responseJson == null) {
			return false;
		}

		return (!Boolean.parseBoolean(responseJson.getString("building")) && responseJson.getInt("duration") > 0);
	}

	/**
	 * wait for build to start
	 * 
	 * @param jobName
	 * @param buildNumber
	 * @throws JSONException
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	public void waitForBuildToStart(String jobName, int buildNumber) throws InterruptedException, TimeoutException,
			JSONException {

		long start = System.currentTimeMillis();
		long timeout = 5 * 60 * 1000;

		while (!isBuildStarted(jobName, buildNumber)) {
			if (System.currentTimeMillis() - start > timeout) {
				throw new TimeoutException("waitForBuildToStart");
			}
			TimeUnit.SECONDS.sleep(5);
		}
	}

	/**
	 * wait for finish
	 * 
	 * @param jobName
	 * @param buildNumber
	 * @param timeoutInMinutes
	 * @throws JSONException
	 * @throws InterruptedException
	 * @throws TimeoutException
	 */
	public void waitForBuildToFinish(String jobName, int buildNumber, int timeoutInMinutes) throws JSONException,
			InterruptedException, TimeoutException {

		long start = System.currentTimeMillis();
		long timeout = timeoutInMinutes * 60 * 1000;

		while (!isBuildFinished(jobName, buildNumber)) {
			if (System.currentTimeMillis() - start > timeout) {
				throw new TimeoutException("waitForBuildToFinish");
			}
			TimeUnit.SECONDS.sleep(5);
		}

	}

	private JSONObject runHttp(DefaultHttpClient client, BasicHttpContext context, String getUrl) {
		HttpGet get = new HttpGet(getUrl);

		HttpResponse response;
		String jsonStr = null;
		try {
			response = client.execute(get, context);
			HttpEntity entity = response.getEntity();
			jsonStr = EntityUtils.toString(entity);
			System.out.println(jsonStr);
			System.out.println(response.getStatusLine());
		} catch (ClientProtocolException e) {
			return null;
		} catch (IOException e) {
			return null;
		}

		return getJsonFromString(jsonStr);
	}

	private JSONObject getJsonFromString(String jsonStr) {

		JSONObject jo = null;
		boolean successJson = false;

		while (!successJson) {
			try {
				jo = new JSONObject(jsonStr);
				successJson = true;
			} catch (JSONException je) {
				String exceptionTypeToFix = "Duplicate key ";
				String duplicated;
				if (je.getMessage().contains(exceptionTypeToFix)) {
					System.out.println("WARNING : Duplicate in json ");
					duplicated = je.getMessage().replaceAll("\"", "").replace("Duplicate key ", "");
					System.out.println("WARNING : Transversing Duplicated json TAG : " + duplicated);
					while (jsonStr.contains(duplicated)) {
						jsonStr = jsonStr.replaceFirst(duplicated,
								duplicated.toUpperCase() + System.currentTimeMillis());
					}
				} else {
					System.out.println("no json");
					return null;
				}
				continue;
			}
		}

		String[] names = JSONObject.getNames(jo);

		for (String string : names) {
			System.out.print(string + " = ");
			try {
				System.out.println(jo.get(string));
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return jo;
	}

	/**
	 * Preemptive authentication interceptor
	 * 
	 */
	private static class PreemptiveAuth implements HttpRequestInterceptor {

		public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
			AuthState authState = (AuthState) context.getAttribute(ClientContext.TARGET_AUTH_STATE);

			if (authState.getAuthScheme() == null) {
				AuthScheme authScheme = (AuthScheme) context.getAttribute("preemptive-auth");
				CredentialsProvider credsProvider = (CredentialsProvider) context
						.getAttribute(ClientContext.CREDS_PROVIDER);
				HttpHost targetHost = (HttpHost) context.getAttribute(ExecutionContext.HTTP_TARGET_HOST);
				if (authScheme != null) {
					Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(), targetHost
							.getPort()));
					if (creds == null) {
						throw new HttpException("No credentials for preemptive authentication");
					}
					authState.setAuthScheme(authScheme);
					authState.setCredentials(creds);
				}
			}

		}

	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setBuildToken(String buildToken) {
		this.buildToken = buildToken;
	}
}
