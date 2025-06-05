import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

public class DeleteJarsFromArtifactory {

    // Replace with your actual Artifactory details
    private static final String ARTIFACTORY_URL = "https://your-domain.jfrog.io/artifactory";
    private static final String REPO_PATH = "libs-release-local/my-folder"; // Your repo and path
    private static final String USERNAME = "your-username";
    private static final String PASSWORD = "your-password";

    public static void main(String[] args) {
        try {
            String listApi = ARTIFACTORY_URL + "/api/storage/" + REPO_PATH + "?list&deep=1";
            HttpURLConnection conn = (HttpURLConnection) new URL(listApi).openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Basic " + encodeAuth());

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                String response = readResponse(conn);
                JSONObject json = new JSONObject(response);
                JSONArray files = json.getJSONArray("files");

                for (int i = 0; i < files.length(); i++) {
                    String fileUri = files.getJSONObject(i).getString("uri");
                    if (fileUri.endsWith(".jar")) {
                        String fullFileUrl = ARTIFACTORY_URL + "/" + REPO_PATH + fileUri;
                        deleteFile(fullFileUrl);
                    }
                }
            } else {
                System.err.println("Failed to list artifacts. HTTP " + responseCode);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void deleteFile(String fileUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(fileUrl).openConnection();
            conn.setRequestMethod("DELETE");
            conn.setRequestProperty("Authorization", "Basic " + encodeAuth());

            int responseCode = conn.getResponseCode();
            if (responseCode == 204) {
                System.out.println("Deleted: " + fileUrl);
            } else {
                System.err.println("Failed to delete: " + fileUrl + " (HTTP " + responseCode + ")");
            }
        } catch (Exception e) {
            System.err.println("Exception while deleting " + fileUrl);
            e.printStackTrace();
        }
    }

    private static String encodeAuth() {
        String credentials = USERNAME + ":" + PASSWORD;
        return Base64.getEncoder().encodeToString(credentials.getBytes());
    }

    private static String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
            sb.append(line);
        reader.close();
        return sb.toString();
    }
}
