package gg.lode.nametag.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class CloudNickService {
   private static final String BASE_URL = "https://lode.gg/api/nametag";
   private static final int TIMEOUT_MS = 5000;
   private final Logger logger;
   private final String serverIp;
   private final int serverPort;

   public CloudNickService(Logger logger, int serverPort) {
      this.logger = logger;
      this.serverPort = serverPort;
      this.serverIp = this.resolveExternalIp();
   }

   private String resolveExternalIp() {
      try {
         URL url = new URL("https://api.ipify.org");
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("GET");
         connection.setConnectTimeout(3000);
         connection.setReadTimeout(3000);
         int responseCode = connection.getResponseCode();
         if (responseCode == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String ip = reader.readLine();
            reader.close();
            connection.disconnect();
            this.logger.info("[Cloud Nick] Resolved external IP: " + ip);
            return ip;
         }

         connection.disconnect();
      } catch (Exception var6) {
         this.logger.warning("[Cloud Nick] Could not resolve external IP: " + var6.getMessage());
      }

      return "unknown";
   }

   public void registerPlayer(UUID uuid, String username) {
      try {
         URL url = new URL("https://lode.gg/api/nametag/add");
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("POST");
         connection.setRequestProperty("Content-Type", "application/json");
         connection.setConnectTimeout(5000);
         connection.setReadTimeout(5000);
         connection.setDoOutput(true);
         JSONObject body = new JSONObject();
         body.put("uuid", uuid.toString());
         body.put("username", username);
         body.put("server_ip", this.serverIp);
         body.put("server_port", this.serverPort);

         try (OutputStream os = connection.getOutputStream()) {
            os.write(body.toJSONString().getBytes(StandardCharsets.UTF_8));
         }

         int responseCode = connection.getResponseCode();
         if (responseCode == 429) {
            this.logger.warning("[Cloud Nick] Rate limited by cloud service for registration of " + username);
         } else if (responseCode != 200) {
            this.logger.warning("[Cloud Nick] Failed to register player " + username + " (HTTP " + responseCode + ")");
         }

         connection.disconnect();
      } catch (Exception var11) {
         this.logger.warning("[Cloud Nick] Could not reach cloud service for registration: " + var11.getMessage());
      }
   }

   @Nullable
   public String getRandomNick() {
      try {
         URL url = new URL("https://lode.gg/api/nametag/random");
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("GET");
         connection.setRequestProperty("X-Server-IP", this.serverIp);
         connection.setRequestProperty("X-Server-Port", String.valueOf(this.serverPort));
         connection.setConnectTimeout(5000);
         connection.setReadTimeout(5000);
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            connection.disconnect();
            if (responseCode == 429) {
               this.logger.warning("[Cloud Nick] Rate limited by cloud service for random nick generation");
            } else {
               this.logger.warning("[Cloud Nick] Failed to get random nick (HTTP " + responseCode + ")");
            }

            return null;
         } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               response.append(line);
            }

            reader.close();
            connection.disconnect();
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject)parser.parse(response.toString());
            return (String)json.get("nick");
         }
      } catch (Exception var9) {
         this.logger.warning("[Cloud Nick] Could not reach cloud service for random nick: " + var9.getMessage());
         return null;
      }
   }
}
