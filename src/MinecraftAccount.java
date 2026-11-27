import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

final class MinecraftAccount {
    // Set in the release build after Mojang approves the project's public client ID.
    static final String REGISTERED_ID="";
    static String clientId(){
        String id=System.getenv().getOrDefault("WOIDWATCH_MICROSOFT_CLIENT_ID",REGISTERED_ID);
        return id.matches("(?i)[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")?id:"";
    }
    static final String OAUTH="https://login.microsoftonline.com/consumers/oauth2/v2.0/";
    static final String MC="https://api.minecraftservices.com/";
    static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    static JsonObject http(String url,String body,String type,String bearer)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Accept","application/json");
        if(bearer!=null)b.header("Authorization","Bearer "+bearer);
        if(body!=null)b.header("Content-Type",type).POST(HttpRequest.BodyPublishers.ofString(body));
        var r=WoidAccount.HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());
        if(r.statusCode()/100!=2){
            if(url.equals(OAUTH+"token")&&r.statusCode()==400&&r.body().contains("\"invalid_grant\""))throw new InvalidSession();
            throw new IOException(URI.create(url).getHost()+" вернул HTTP "+r.statusCode());
        }
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }
    static class InvalidSession extends IOException {}
    static JsonObject exchange(String url,JsonObject properties,String relying)throws Exception{
        var req=new JsonObject();req.add("Properties",properties);req.addProperty("RelyingParty",relying);req.addProperty("TokenType","JWT");return http(url,req.toString(),"application/json",null);
    }
    record Identity(String name,String uuid,String access,String xuid){}
    static Identity signIn(String community)throws Exception{
        String id=clientId();if(id.isEmpty())throw new IOException("Доступ WoidWatch к Minecraft Services ещё не настроен.");
        String slot="mc-"+community.toLowerCase(Locale.ROOT);
        if(!slot.matches("mc-[a-z0-9_]{3,24}"))throw new IOException("Неверный профиль WoidWatch");
        Path file=LauncherUpdates.home().resolve(slot+".session");JsonObject token=null;
        if(Files.exists(file)){
            String refresh=WoidAccount.protect(Files.readString(file),false,slot);
            try{token=http(OAUTH+"token","client_id="+id+"&grant_type=refresh_token&refresh_token="+enc(refresh),"application/x-www-form-urlencoded",null);}
            catch(InvalidSession e){Files.delete(file);}
        }
        if(token==null)token=DeviceSignIn.login(id);
        if(token.has("refresh_token")){
            String saved=WoidAccount.protect(token.get("refresh_token").getAsString(),true,slot);
            Pack.write(file,Frontier.MAC?"keychain":saved);
        }
        var props=new JsonObject();props.addProperty("AuthMethod","RPS");props.addProperty("SiteName","user.auth.xboxlive.com");props.addProperty("RpsTicket","d="+token.get("access_token").getAsString());
        var xbl=exchange("https://user.auth.xboxlive.com/user/authenticate",props,"http://auth.xboxlive.com");
        var tokens=new JsonArray();tokens.add(xbl.get("Token"));props=new JsonObject();props.addProperty("SandboxId","RETAIL");props.add("UserTokens",tokens);
        var xsts=exchange("https://xsts.auth.xboxlive.com/xsts/authorize",props,"rp://api.minecraftservices.com/");
        var claim=xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
        var login=new JsonObject();login.addProperty("identityToken","XBL3.0 x="+claim.get("uhs").getAsString()+";"+xsts.get("Token").getAsString());
        String access=http(MC+"authentication/login_with_xbox",login.toString(),"application/json",null).get("access_token").getAsString();
        var profile=http(MC+"minecraft/profile",null,null,access);
        String uuid=profile.get("id").getAsString(),name=profile.get("name").getAsString();
        if(!uuid.matches("[a-fA-F0-9]{32}")||!name.matches("[a-zA-Z0-9_]{1,16}"))throw new IOException("Профиль Minecraft Java недоступен");
        return new Identity(name,uuid,access,claim.has("xid")?claim.get("xid").getAsString():"");
    }
}
