package gg.lode.nametagapi;

public class NameTagAPI {
   private static INameTagAPI api;

   public static void setApi(INameTagAPI api) {
      NameTagAPI.api = api;
   }

   public static INameTagAPI getApi() {
      return api;
   }
}
