package wnick.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class MojangSkinFetcher {
   public static UUID fetchUUID(String username) {
      try {
         String apiEndpoint = "https://api.mojang.com/users/profiles/minecraft/" + username;
         URL url = new URL(apiEndpoint);
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("GET");
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            return null;
         } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               response.append(line);
            }

            reader.close();
            String jsonResponse = response.toString();
            if (jsonResponse.isEmpty()) {
               return null;
            } else {
               JSONParser parser = new JSONParser();
               JSONObject object = (JSONObject)parser.parse(jsonResponse);
               String uuidString = (String)object.get("id");
               if (uuidString != null && !uuidString.contains("-")) {
                  uuidString = uuidString.substring(0, 8)
                     + "-"
                     + uuidString.substring(8, 12)
                     + "-"
                     + uuidString.substring(12, 16)
                     + "-"
                     + uuidString.substring(16, 20)
                     + "-"
                     + uuidString.substring(20);
               }

               return UUID.fromString(uuidString);
            }
         }
      } catch (Exception var12) {
         var12.printStackTrace();
         return null;
      }
   }

   public static String[] fetchSkinData(UUID uuid) {
      try {
         String apiEndpoint = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "") + "?unsigned=false";
         URL url = new URL(apiEndpoint);
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("GET");
         int responseCode = connection.getResponseCode();
         if (responseCode == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               response.append(line);
            }

            reader.close();
            String jsonResponse = response.toString();
            JSONParser parser = new JSONParser();
            JSONObject profile = (JSONObject)parser.parse(jsonResponse);

            for (Object prop : (JSONArray)profile.get("properties")) {
               JSONObject property = (JSONObject)prop;
               String name = (String)property.get("name");
               if ("textures".equals(name)) {
                  String value = (String)property.get("value");
                  String signature = (String)property.get("signature");
                  if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
                     value = value.substring(1, value.length() - 1);
                  }

                  if (signature != null && signature.startsWith("\"") && signature.endsWith("\"")) {
                     signature = signature.substring(1, signature.length() - 1);
                  }

                  return new String[]{value, signature};
               }
            }
         }

         return null;
      } catch (Exception var18) {
         var18.printStackTrace();
         return null;
      }
   }

   public static String[] fetchSkinDataFromUsername(String username) {
      UUID uuid = fetchUUID(username);
      return uuid == null ? null : fetchSkinData(uuid);
   }

   public static String[] fetchMojangProfile(UUID uuid) {
      try {
         String apiEndpoint = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "") + "?unsigned=false";
         URL url = new URL(apiEndpoint);
         HttpURLConnection connection = (HttpURLConnection)url.openConnection();
         connection.setRequestMethod("GET");
         connection.setConnectTimeout(5000);
         connection.setReadTimeout(5000);
         int responseCode = connection.getResponseCode();
         if (responseCode != 200) {
            return null;
         } else {
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null) {
               response.append(line);
            }

            reader.close();
            String jsonResponse = response.toString();
            JSONParser parser = new JSONParser();
            JSONObject profile = (JSONObject)parser.parse(jsonResponse);
            String name = (String)profile.get("name");
            String texture = null;
            String signature = null;
            JSONArray properties = (JSONArray)profile.get("properties");
            if (properties != null) {
               for (Object prop : properties) {
                  JSONObject property = (JSONObject)prop;
                  String propName = (String)property.get("name");
                  if ("textures".equals(propName)) {
                     texture = (String)property.get("value");
                     signature = (String)property.get("signature");
                     break;
                  }
               }
            }

            return new String[]{name, texture, signature};
         }
      } catch (Exception var19) {
         var19.printStackTrace();
         return null;
      }
   }
}
