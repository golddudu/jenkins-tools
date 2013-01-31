simple jenkins api
to use for build jenkins build from java automatically

including JenkinsApi class that can be copied to other project and used independently, or use it from this project as jar

used for :

* build job with or w/o parameters
* check build status
* wait for build to start and finish

- build the maven project : #> mvn clean install

Sample use: create your private Jenkins Class and extend JenkisApi 

	public class RavelloJenkins extends JenkinsApi {

		private static final String HOST = "localhost";
		private static final String PORT = "8080";

		private static final String USERNAME = "userdudu";
		private static final String PASSWORD = "passdudu";
		private static final String TOKEN = "asdasdasdasdasdasdasdasdasdasdasdasdas";

		public RavelloJenkins() {
			super(HOST, PORT, USERNAME, PASSWORD, TOKEN);
		}

		public static void main(String[] args) throws Exception {

			RavelloJenkins jenkins = new RavelloJenkins();

			String jobName = "Builder-Job";

			HashMap<String, String> params = new HashMap<String, String>() {
				{
					put("Profile", "a");
					put("url", "ravellosystems.com");
					put("svn_revision", "c");
					put("installation.packager_dir", "d");
					put("server-deploy-locations", "f");
					put("default-cloud-keys", "e");
					put("cloudinstances.location", "r");
				}
			};

			jenkins.buildWithParamAndWaitTillFinished(jobName, params);

		}

	}
