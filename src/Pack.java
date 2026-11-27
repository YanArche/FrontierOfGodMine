import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

final class Pack {
    static final String PROJECT = "YanArche/FrontierOfGodMine";
    static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
    final Path game, resources, state;
    final boolean server;
    final Consumer<String> log;
    Pack(Path game, Path resources, boolean server, Consumer<String> log) {
        this.game=game.toAbsolutePath().normalize(); this.resources=resources; this.server=server; this.log=log;
        this.state=this.game.resolve(".frontier");
    }
    static JsonObject read(Path p) throws IOException {return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}
    static void move(Path a, Path b) throws IOException {
        Files.createDirectories(b.getParent());
        try {Files.move(a,b,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(a,b,StandardCopyOption.REPLACE_EXISTING);}
    }
    static void write(Path p,String s) throws IOException {
        Files.createDirectories(p.getParent());Path tmp=Files.createTempFile(p.getParent(),"update-",".tmp");
        try{Files.writeString(tmp,s);move(tmp,p);}finally{Files.deleteIfExists(tmp);}
    }
    static String digest(Path p) throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(var in=new DigestInputStream(Files.newInputStream(p),md)){in.transferTo(OutputStream.nullOutputStream());}
        return HexFormat.of().formatHex(md.digest());
    }
    static void get(String url,Path target) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(url)).header("User-Agent","FrontierOfGod-launcher").timeout(Duration.ofMinutes(10)).GET().build();
        var response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());
        try(var in=response.body()){
            if(response.statusCode()!=200)throw new IOException("HTTP "+response.statusCode()+" "+url);
            Files.copy(in,target,StandardCopyOption.REPLACE_EXISTING);
        }
    }
    static boolean managed(String key){
        return !key.contains("\\")&&!key.contains(":")&&!key.contains("..")&&
            (key.matches("mods/[^/]+\\.jar")||key.startsWith("config/")||key.startsWith("resourcepacks/")||
             key.startsWith("datapacks/")||key.startsWith("defaultconfigs/")||key.startsWith("shaderpacks/")||
             key.equals("options.txt")||key.matches("essential/[^/]+")||key.equals("CustomSkinLoader/CustomSkinLoader.json"));
    }
    Path safe(String key) throws IOException {
        if(!managed(key))throw new IOException("Недопустимый файл: "+key);
        Path p=game.resolve(key).normalize();
        if(!p.startsWith(game))throw new IOException(key);
        for(Path x=p;!x.equals(game);x=x.getParent())if(Files.isSymbolicLink(x))throw new IOException("Символическая ссылка: "+x);
        return p;
    }
    static void clean(Path p)throws IOException{
        if(!Files.exists(p))return;
        try(var paths=Files.walk(p)){for(Path f:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(f);}
    }
    void recover(Path tx)throws IOException{
        Path journal=tx.resolve("journal.json");
        if(Files.exists(journal)){
            var actions=read(journal);
            for(String key:actions.keySet()){
                Path to=safe(key),backup=tx.resolve("old").resolve(key);
                if(actions.get(key).getAsBoolean()){
                    if(Files.exists(backup))move(backup,to);
                }else Files.deleteIfExists(to);
            }
        }
        clean(tx);
    }
    void update(boolean offline)throws Exception{
        Files.createDirectories(state);
        Path tx=state.resolve("transaction"),current=state.resolve("index.json"),seed=resources.resolve("index.json");
        recover(tx);Files.createDirectories(tx);
        Path next=tx.resolve("index.json");
        String raw=null;
        if(!offline){
            try{
                Path ref=tx.resolve("ref");get("https://api.github.com/repos/"+PROJECT+"/commits/main",ref);
                String commit=read(ref).get("sha").getAsString();if(!commit.matches("[0-9a-f]{40}"))throw new IOException("Git revision");
                raw="https://raw.githubusercontent.com/"+PROJECT+"/"+commit+"/";
                get(raw+"pack/index.json",next);
            }catch(Exception e){log.accept("Канал обновления недоступен. "+e.getMessage());raw=null;}
        }
        if(raw==null)Files.copy(Files.exists(current)?current:seed,next,StandardCopyOption.REPLACE_EXISTING);
        JsonObject index=read(next);
        if(index.get("format").getAsInt()!=1 || !index.get("neoforge").getAsString().equals("21.1.267"))throw new IOException("Требуется новая версия приложения.");
        JsonObject files=index.getAsJsonObject("files"),old=Files.exists(current)?read(current).getAsJsonObject("files"):new JsonObject();
        JsonObject seeds=read(seed).getAsJsonObject("files"),changes=new JsonObject();
        Path override=tx.resolve("overrides.zip"); ZipFile extras=null;
        try(var bundle=new ZipFile(resources.resolve("payload.zip").toFile())){
            for(var pair:files.entrySet()){
                String key=pair.getKey();var item=pair.getValue().getAsJsonObject();
                if(server&&!item.get("server").getAsBoolean())continue;
                Path target=safe(key);
                if(Files.exists(target)&&(key.equals("options.txt")||key.equals("config/simplerkeybinds.json")))continue;
                String hash=item.get("sha256").getAsString();
                if(Files.isRegularFile(target)&&digest(target).equals(hash))continue;
                log.accept("Обновление: "+key);
                Path staged=tx.resolve("new").resolve(key);Files.createDirectories(staged.getParent());
                var local=bundle.getEntry(key);
                if(local!=null&&seeds.has(key)&&seeds.getAsJsonObject(key).get("sha256").getAsString().equals(hash)){
                    try(var in=bundle.getInputStream(local)){Files.copy(in,staged);}
                }else if(raw==null)throw new IOException("Для восстановления "+key+" нужен интернет.");
                else if(item.has("url"))get(item.get("url").getAsString(),staged);
                else if(item.has("repo"))get(raw+item.get("repo").getAsString().replace(" ","%20"),staged);
                else{
                    if(extras==null){get(raw+"pack/overrides.zip",override);if(!digest(override).equals(index.get("overrides").getAsString()))throw new IOException("Хеш ресурсов");extras=new ZipFile(override.toFile());}
                    try(var in=extras.getInputStream(extras.getEntry(key))){Files.copy(in,staged);}
                }
                if(!digest(staged).equals(hash))throw new IOException("Хеш не совпал: "+key);
                changes.addProperty(key,Files.exists(target));
            }
        }finally{if(extras!=null)extras.close();}
        for(String key:old.keySet()){
            boolean removed=!files.has(key)||(server&&!files.getAsJsonObject(key).get("server").getAsBoolean());
            if(removed&&key.startsWith("mods/")&&Files.exists(safe(key)))changes.addProperty(key,true);
        }
        write(tx.resolve("journal.json"),JSON.toJson(changes));
        try{
            for(String key:changes.keySet()){
                Path target=safe(key),stage=tx.resolve("new").resolve(key);
                if(Files.exists(target))move(target,tx.resolve("old").resolve(key));
                if(Files.exists(stage))move(stage,target);
            }
            // Removing the journal commits the file set; a stale index is safe to replay.
            Files.delete(tx.resolve("journal.json"));move(next,current);
        }catch(Exception e){recover(tx);throw e;}
        clean(tx);log.accept("Файлы сборки проверены.");
    }
}
