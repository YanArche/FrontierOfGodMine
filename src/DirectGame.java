import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

/** Direct launch is restricted to the shipped 1.21.1 / NeoForge profile. */
final class DirectGame {
    static boolean allowed(JsonObject o){
        if(!o.has("rules"))return true;
        boolean allow=false;String os=Frontier.MAC?"osx":"windows";
        for(var item:o.getAsJsonArray("rules")){
            var r=item.getAsJsonObject();if(r.has("features"))continue;
            if(r.has("os")){
                var filter=r.getAsJsonObject("os");
                if(filter.has("name")&&!os.equals(filter.get("name").getAsString()))continue;
                if(filter.has("arch")&&!System.getProperty("os.arch").equals(filter.get("arch").getAsString()))continue;
                if(filter.has("version")&&!System.getProperty("os.version").matches(filter.get("version").getAsString()))continue;
            }
            allow="allow".equals(r.get("action").getAsString());
        }
        return allow;
    }
    static String sha1(Path f)throws Exception{
        var md=MessageDigest.getInstance("SHA-1");try(var stream=new DigestInputStream(Files.newInputStream(f),md)){stream.transferTo(OutputStream.nullOutputStream());}
        return HexFormat.of().formatHex(md.digest());
    }
    static void fetch(Path target,String url,String sha)throws Exception{
        if(!sha.matches("[0-9a-f]{40}"))throw new IOException("Некорректный SHA-1");
        if(Files.isRegularFile(target)&&sha.equals(sha1(target)))return;
        if(!"https".equals(URI.create(url).getScheme()))throw new IOException("Нужен HTTPS");
        Files.createDirectories(target.getParent());Path tmp=Files.createTempFile(target.getParent(),"asset-",".tmp");
        try{Pack.get(url,tmp);if(!sha.equals(sha1(tmp)))throw new IOException("Ошибка проверки "+target.getFileName());Pack.move(tmp,target);}
        finally{Files.deleteIfExists(tmp);}
    }
    static void fetch(Path file,JsonObject desc)throws Exception{fetch(file,desc.get("url").getAsString(),desc.get("sha1").getAsString());}
    static JsonObject vanilla(Path base)throws Exception{
        var req=HttpRequest.newBuilder(URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")).timeout(java.time.Duration.ofSeconds(30)).build();
        var res=WoidAccount.HTTP.send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()!=200)throw new IOException("Каталог Minecraft недоступен");
        for(var e:JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("versions")){
            var v=e.getAsJsonObject();if(!v.get("id").getAsString().equals("1.21.1"))continue;
            Path path=base.resolve("versions/1.21.1/1.21.1.json");fetch(path,v);return Pack.read(path);
        }
        throw new IOException("Minecraft 1.21.1 отсутствует в каталоге");
    }
    static void arguments(List<String> out,JsonArray args,Map<String,String> vars)throws IOException{
        for(var arg:args){
            List<String> values=new ArrayList<>();
            if(arg.isJsonPrimitive())values.add(arg.getAsString());
            else{var o=arg.getAsJsonObject();if(!allowed(o))continue;var v=o.get("value");if(v.isJsonArray())for(var x:v.getAsJsonArray())values.add(x.getAsString());else values.add(v.getAsString());}
            for(String s:values){for(var kv:vars.entrySet())s=s.replace("${"+kv.getKey()+"}",kv.getValue());if(s.contains("${"))throw new IOException("Неподдерживаемый параметр профиля");out.add(s);}
        }
    }
    static List<String> classpath(Path base,JsonObject mc,JsonObject neo,boolean local)throws Exception{
        var files=new LinkedHashMap<String,String>();Path libraries=base.resolve("libraries");
        for(JsonObject version:List.of(neo,mc))for(var el:version.getAsJsonArray("libraries")){
            var lib=el.getAsJsonObject();if(!allowed(lib))continue;
            var art=lib.getAsJsonObject("downloads").getAsJsonObject("artifact");
            String[] name=lib.get("name").getAsString().split(":");String key=name[0]+":"+name[1]+(name.length>3?":"+name[3]:"");
            if(files.containsKey(key))continue;
            Path path=libraries.resolve(art.get("path").getAsString()).normalize();
            if(!path.startsWith(libraries))throw new IOException("Путь библиотеки");
            if(local){if(!Files.isRegularFile(path))throw new IOException("Не установлена библиотека "+path.getFileName());}
            else if(!Files.isRegularFile(path)||art.has("url")&&!art.get("url").getAsString().isBlank())fetch(path,art);
            files.put(key,path.toString());
        }
        return new ArrayList<>(files.values());
    }
    static void run(Path game,MinecraftAccount.Identity identity,boolean local,Consumer<String> log)throws Exception{
        run(game,identity,local,log,false);
    }
    static void run(Path game,MinecraftAccount.Identity identity,boolean local,Consumer<String> log,boolean verifyOnly)throws Exception{
        run(game,identity,local,log,verifyOnly,"");
    }
    static void run(Path game,MinecraftAccount.Identity identity,boolean local,Consumer<String> log,boolean verifyOnly,String address)throws Exception{
        Path base=Files.exists(Frontier.mc().resolve("versions/1.21.1/1.21.1.json"))?Frontier.mc():LauncherUpdates.home().resolve("minecraft");
        log.accept("Проверка файлов Minecraft и NeoForge…");
        JsonObject mc;
        if(local){
            Path version=base.resolve("versions/1.21.1/1.21.1.json");
            if(!Files.isRegularFile(version)||!Files.isRegularFile(version.resolveSibling("1.21.1.jar"))||
                !Files.isRegularFile(base.resolve("versions/"+Frontier.ID+"/"+Frontier.ID+".json")))
                throw new IOException("Для локальной игры установите Minecraft 1.21.1 и NeoForge 21.1.267 на этом компьютере.");
            mc=Pack.read(version);
        }else{
        Files.createDirectories(base);
        if(!Files.exists(base.resolve("launcher_profiles.json"))&&!base.equals(Frontier.mc()))Pack.write(base.resolve("launcher_profiles.json"),"{\"profiles\":{}}");
        mc=vanilla(base);
        fetch(base.resolve("versions/1.21.1/1.21.1.jar"),mc.getAsJsonObject("downloads").getAsJsonObject("client"));
        Frontier.loader(base,false,log);
        }
        var neo=Pack.read(base.resolve("versions/"+Frontier.ID+"/"+Frontier.ID+".json"));
        if(!"1.21.1".equals(neo.get("inheritsFrom").getAsString()))throw new IOException("Неподдерживаемый профиль NeoForge");
        var cp=classpath(base,mc,neo,local);
        Path assets=base.resolve("assets");var ai=mc.getAsJsonObject("assetIndex");String index=ai.get("id").getAsString();
        Path aiFile=assets.resolve("indexes/"+index+".json");if(!local)fetch(aiFile,ai);
        var objects=Pack.read(aiFile).getAsJsonObject("objects");int done=0;
        for(var ent:objects.entrySet()){
            String h=ent.getValue().getAsJsonObject().get("hash").getAsString();if(!h.matches("[a-f0-9]{40}"))throw new IOException("Повреждён индекс ресурсов");
            String rel=h.substring(0,2)+"/"+h;if(local){if(!Files.isRegularFile(assets.resolve("objects/"+rel)))throw new IOException("Отсутствует ресурс Minecraft: "+ent.getKey());}
            else fetch(assets.resolve("objects/"+rel),"https://resources.download.minecraft.net/"+rel,h);
            if(++done%64==0||done==objects.size())log.accept("Ресурсы Minecraft: "+done+" / "+objects.size());
        }
        Path natives=game.resolve(".frontier/natives");Files.createDirectories(natives);
        var vars=Map.ofEntries(Map.entry("classpath",String.join(File.pathSeparator,cp)),Map.entry("library_directory",base.resolve("libraries").toString()),Map.entry("classpath_separator",File.pathSeparator),
            Map.entry("auth_player_name",identity.name()),Map.entry("auth_uuid",identity.uuid()),Map.entry("auth_access_token",identity.access()),Map.entry("auth_xuid",identity.xuid()),
            Map.entry("clientid",MinecraftAccount.clientId()),Map.entry("user_type","msa"),Map.entry("version_type","release"),Map.entry("version_name",Frontier.ID),Map.entry("game_directory",game.toString()),
            Map.entry("assets_root",assets.toString()),Map.entry("assets_index_name",index),Map.entry("natives_directory",natives.toString()),Map.entry("launcher_name","WoidWatch"),Map.entry("launcher_version",LauncherUpdates.VERSION));
        var jvm=new ArrayList<>(List.of("-Xmx"+LauncherSettings.memory(LauncherSettings.read())+"G","-Xms512M"));var args=new ArrayList<String>();
        for(var version:List.of(mc,neo)){var a=version.getAsJsonObject("arguments");arguments(jvm,a.getAsJsonArray("jvm"),vars);arguments(args,a.getAsJsonArray("game"),vars);}
        jvm.add("-Dcustomskinloader.ignorePatchFailure=true");
        boolean test=AccountPanel.test();
        String gameTicket=null, gameProfile=null;
        if(!verifyOnly){
            try{
                var grant=AccountPanel.api(test,"/desktop/game-ticket",new JsonObject(),AccountPanel.token(test));
                if(grant!=null){gameTicket=grant.get("ticket").getAsString();gameProfile=grant.get("profile").getAsString();}
            }catch(Exception unavailable){log.accept("Сетевой вход WoidWatch пока недоступен");}
        }
        if(gameTicket==null)args.add("--disableMultiplayer");
        else{
            String endpoint=test?"http://127.0.0.1:18787":WoidAccount.ORIGIN;
            jvm.add("-Dminecraft.api.session.host="+endpoint);
            jvm.add("-Dminecraft.api.services.host="+endpoint);
        }
        if(!address.isEmpty()){
            if(gameTicket==null)throw new IOException("Для входа на сервер нужен игровой билет WoidWatch.");
            args.add("--quickPlayMultiplayer");args.add(Community.address(address));
        }
        if(verifyOnly){jvm.set(1,"-Xms32M");jvm.add("--dry-run");}
        jvm.add(neo.get("mainClass").getAsString());
        Path argfile=Files.createTempFile(game.resolve(".frontier"),"jvm-",".args");
        try{
            Files.write(argfile,jvm.stream().map(s->"\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"").toList());
            var cmd=new ArrayList<>(List.of(Frontier.java().toString(),"@"+argfile));cmd.addAll(args);
            log.accept("Запуск Java 21…");
            var builder=new ProcessBuilder(cmd).directory(game.toFile()).redirectErrorStream(true);
            if(!verifyOnly){
                try(var resource=DirectGame.class.getResourceAsStream("/woidwatch-wardrobe.jar")){
                    if(resource==null)throw new IOException("Отсутствует мод гардероба.");
                    Path mods=game.resolve("mods");Files.createDirectories(mods);
                    Path tmp=Files.createTempFile(mods,"ww-",".tmp");
                    try{Files.copy(resource,tmp,StandardCopyOption.REPLACE_EXISTING);Pack.move(tmp,mods.resolve("woidwatch-wardrobe.jar"));}finally{Files.deleteIfExists(tmp);}
                }
                if(gameTicket!=null){builder.environment().put("WW_GAME_TICKET",gameTicket);builder.environment().put("WW_GAME_PROFILE",gameProfile);}
                try{var ticket=AccountPanel.api(test,"/desktop/wardrobe/ticket",new JsonObject(),AccountPanel.token(test));
                    if(ticket!=null){builder.environment().put("WW_COSMETICS",ticket.get("ticket").getAsString());builder.environment().put("WW_COSMETICS_MODE",test?"test":"production");}
                }catch(Exception unavailable){log.accept("Сервис гардероба недоступен");}
            }
            var p=builder.start();
            try(var lines=p.inputReader()){
                String line;
                while((line=lines.readLine())!=null){
                    if(line.contains("NeoForge mod loading"))log.accept("NeoForge загружает моды");
                    else if(line.contains("Reloading ResourceManager"))log.accept("Minecraft загружает ресурспаки");
                    else if(line.contains("Sound engine started"))log.accept("Звук игры готов");
                    else if(line.contains("minecraft:textures/atlas/gui.png-atlas"))log.accept("Интерфейс Minecraft загружен");
                }
            }
            if(p.waitFor()!=0)throw new IOException("Minecraft завершился с ошибкой. Проверьте logs/latest.log в папке сборки.");
        }finally{Files.deleteIfExists(argfile);}
    }
}
