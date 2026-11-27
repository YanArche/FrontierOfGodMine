import com.google.gson.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class LauncherChecks {
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static JsonObject signed(Path file,PrivateKey key,int build)throws Exception{
        JsonObject data=new JsonObject();
        data.addProperty("product","woidwatch-launcher");data.addProperty("version","1.2.0");
        data.addProperty("build",build);data.addProperty("sha256",Pack.digest(file));data.addProperty("bytes",Files.size(file));
        byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        var sig=Signature.getInstance("SHA256withRSA");sig.initSign(key);sig.update(bytes);
        JsonObject result=new JsonObject();result.addProperty("manifest",Base64.getEncoder().encodeToString(bytes));
        result.addProperty("signature",Base64.getEncoder().encodeToString(sig.sign()));return result;
    }
    public static void main(String[] args)throws Exception{
        Path temp=Path.of(args[0]);Path root=LauncherUpdates.home();Files.createDirectories(root);
        require(root.startsWith(temp),"requires isolated user profile");
        Path game=temp.resolve("existing");Files.createDirectories(game.resolve("mods"));Files.createFile(game.resolve("mods/frontier-campaign-data-qa.jar"));
        Files.createDirectories(game.resolve("saves/test"));Files.writeString(game.resolve("saves/test/level.dat"),"world");
        Files.writeString(game.resolve("options.txt"),"key_key.jump:key.keyboard.z");
        Library.link(game);require(Files.isSameFile(Library.location(),game),"incorrect instance");
        require(Files.readString(game.resolve("options.txt")).endsWith(".z")&&Files.readString(game.resolve("saves/test/level.dat")).equals("world"),"personal data changed");
        KeyStore keys=KeyStore.getInstance("PKCS12");try(var in=Files.newInputStream(temp.resolve("qa.p12"))){keys.load(in,"testing".toCharArray());}
        var key=(PrivateKey)keys.getKey("test","testing".toCharArray());
        Path good=Frontier.resources.resolve("frontier.jar"),bad=temp.resolve("broken.jar");
        Files.writeString(bad,"broken application");
        JsonObject trusted=signed(good,key,LauncherUpdates.BUILD+1),broken=signed(bad,key,LauncherUpdates.BUILD+2);
        var forged=trusted.deepCopy();forged.add("manifest",broken.get("manifest"));
        try{LauncherUpdates.verify(forged);throw new AssertionError("forgery accepted");}catch(java.io.IOException expected){}
        JsonObject state=new JsonObject();
        for(var item:List.of(trusted,broken)){
            var r=LauncherUpdates.verify(item);var f=LauncherUpdates.jar(root,r);
            Files.copy(r.build()==LauncherUpdates.BUILD+1?good:bad,f);Pack.write(root.resolve("pending.json"),item.toString());LauncherUpdates.promote(root,state);
        }
        LauncherUpdates.run();state=Pack.read(root.resolve("active.json"));
        require(state.get("highest").getAsInt()==LauncherUpdates.BUILD+2&&LauncherUpdates.verify(state.getAsJsonObject("active")).build()==LauncherUpdates.BUILD+1,"rollback failed");
        require(Files.exists(root.resolve("fallback-ran")),"healthy child did not start");
        Pack.write(root.resolve("pending.json"),trusted.toString());LauncherUpdates.promote(root,state);
        require(state.get("highest").getAsInt()==LauncherUpdates.BUILD+2,"accepted downgrade");
        Files.writeString(LauncherUpdates.jar(root,LauncherUpdates.verify(trusted)),"tampered");
        try{LauncherUpdates.select(root,trusted);throw new AssertionError("modified binary accepted");}catch(java.io.IOException expected){}
        System.out.println("PASS: import, signature, hash, staged activation, startup rollback, working child, downgrade rejection");
    }
}
