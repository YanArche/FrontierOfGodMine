import com.google.gson.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class Frontier {
    static final String ID="neoforge-21.1.267", PROFILE="frontier_of_god";
    static final boolean MAC=System.getProperty("os.name").startsWith("Mac");
    static final Path HOME=Path.of(System.getProperty("user.home"));
    static Path resources=Path.of(System.getProperty("frontier.resources",".")).toAbsolutePath();
    static Path java(){return Path.of(System.getProperty("java.home"),"bin",MAC?"java":"java.exe");}
    static Path mc(){return MAC?HOME.resolve("Library/Application Support/minecraft"):Path.of(System.getenv("APPDATA"),".minecraft");}
    static Path game(){return MAC?HOME.resolve("Library/Application Support/Frontier of God/game"):Path.of(System.getenv("LOCALAPPDATA"),"FrontierOfGod","game");}
    static void profiles(Path base,Path game)throws Exception{
        boolean found=false;
        for(String name:List.of("launcher_profiles.json","launcher_profiles_microsoft_store.json")){
            Path f=base.resolve(name);if(!Files.isRegularFile(f))continue;
            JsonObject doc=Pack.read(f),all=doc.getAsJsonObject("profiles");
            if(all==null)throw new IOException("Ошибка файла профилей "+f);
            JsonObject profile=all.has(PROFILE)?all.getAsJsonObject(PROFILE):new JsonObject();
            profile.addProperty("name","FRONTIER OF GOD");profile.addProperty("type","custom");profile.addProperty("icon","Grass");
            profile.addProperty("lastVersionId",ID);profile.addProperty("gameDir",game.toString());
            profile.addProperty("lastUsed",Instant.now().toString());if(!profile.has("javaArgs"))profile.addProperty("javaArgs","-Xmx6G -Xms512M");
            all.add(PROFILE,profile);doc.addProperty("selectedProfile",PROFILE);saveLauncher(f,Pack.JSON.toJson(doc));found=true;
        }
        if(!found)throw new IOException("Сначала войдите в Minecraft Launcher, откройте Java Edition и закройте лаунчер.");
        for(String name:List.of("launcher_ui_state.json","launcher_ui_state_microsoft_store.json")){
            Path f=base.resolve(name);if(!Files.exists(f))continue;String text=Files.readString(f);int start=text.indexOf('{');if(start<0)continue;
            JsonObject doc=JsonParser.parseString(text.substring(start)).getAsJsonObject();
            var data=doc.getAsJsonObject("data");if(data==null||!data.has("UiSettings"))continue;
            var ui=JsonParser.parseString(data.get("UiSettings").getAsString()).getAsJsonObject();
            ui.addProperty("lastVisitedPage","java");data.addProperty("UiSettings",Pack.JSON.toJson(ui));
            saveLauncher(f,text.substring(0,start)+Pack.JSON.toJson(doc));
        }
    }
    static void saveLauncher(Path file,String text)throws IOException{
        Path backup=file.resolveSibling(file.getFileName()+".frontier-backup");
        if(!Files.exists(backup))Files.copy(file,backup);
        Pack.write(file,text);
    }
    static boolean launcherOpen(){
        return ProcessHandle.allProcesses().anyMatch(p->p.info().command().map(c->{
            String n=Path.of(c).getFileName().toString().toLowerCase(Locale.ROOT);
            return n.equals("minecraftlauncher.exe")||c.contains("/Minecraft.app/Contents/MacOS/");
        }).orElse(false));
    }
    static void openLauncher()throws Exception{
        if(MAC){new ProcessBuilder("open","-b","com.mojang.minecraftlauncher").start();return;}
        for(String env:List.of("ProgramFiles(x86)","ProgramFiles")){
            String root=System.getenv(env);if(root==null)continue;Path f=Path.of(root,"Minecraft Launcher","MinecraftLauncher.exe");
            if(Files.exists(f)){new ProcessBuilder(f.toString()).start();return;}
        }
        new ProcessBuilder("explorer.exe","shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft").start();
    }
    static void loader(Path target,boolean server,Consumer<String> log)throws Exception{
        Path ready=target.resolve(server?"libraries/net/neoforged/neoforge/21.1.267/win_args.txt":"versions/"+ID+"/"+ID+".json");
        if(Files.isRegularFile(ready)&&Files.isRegularFile(target.resolve("libraries/net/neoforged/neoforge/21.1.267/neoforge-21.1.267-"+(server?"server":"client")+".jar")))return;
        log.accept("Установка NeoForge 21.1.267…");
        Path jar=resources.resolve("loader.jar");if(!Pack.digest(jar).equals("25bbe66e82205abeccdfd23bdac61e6a8e9ff1333885f3c9f31d392a5ec97e60"))throw new IOException("Повреждён установщик NeoForge.");
        Path output=target.resolve("frontier-neoforge.log");
        int code=new ProcessBuilder(java().toString(),"-jar",jar.toString(),server?"--installServer":"--installClient",target.toString()).directory(target.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start().waitFor();
        if(code!=0||!Files.exists(ready))throw new IOException("Ошибка NeoForge. Журнал: "+output);
    }
    static void run(Path target,boolean server,boolean prepare,boolean offline,Consumer<String> log)throws Exception{
        Files.createDirectories(target.resolve(".frontier"));
        try(var ch=FileChannel.open(target.resolve(".frontier/run.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=ch.tryLock()){
            if(lock==null)throw new IOException("Фронтир уже запущен.");
            boolean direct=!server&&!prepare&&!MinecraftAccount.clientId().isEmpty();
            String member=null;if(direct){var me=WoidAccount.current();if(me==null)throw new IOException("Войдите через «Мой аккаунт».");member=me.get("name").getAsString();}
            new Pack(target,resources,server,log).update(offline);
            if(prepare)return;
            if(server)loader(target,true,log);
            if(server){
                Path eula=target.resolve("eula.txt");
                if(!Files.exists(eula))Pack.write(eula,"# https://aka.ms/MinecraftEULA\neula=false\n");
                if(Files.readAllLines(eula).stream().noneMatch(s->s.trim().equals("eula=true")))throw new IOException("Прочитайте EULA и подтвердите согласие в "+eula);
                String argfile="@libraries/net/neoforged/neoforge/21.1.267/win_args.txt";
                int code=new ProcessBuilder(java().toString(),"@user_jvm_args.txt",argfile,"nogui").directory(target.toFile()).inheritIO().start().waitFor();
                if(code!=0)throw new IOException("Сервер завершился с кодом "+code);
            }else{String communityAddress=null;try{communityAddress=Community.refresh();}catch(Exception e){log.accept("Адрес сервера временно недоступен.");}
                if(communityAddress!=null)ServerListFile.sync(target,communityAddress);if(direct){DirectGame.run(target,member,log);return;}PrismGame.launch(target,log);}
        }
    }
    public static void main(String[] args)throws Exception{
        if(args.length==0){try{LauncherUpdates.run();}catch(Exception e){JOptionPane.showMessageDialog(null,e.toString(),"WoidWatch",JOptionPane.ERROR_MESSAGE);}return;}
        if(args[0].equals("--library")){SwingUtilities.invokeLater(()->Library.open(Path.of(args[1])));return;}
        var flags=Arrays.asList(args);Path target=Path.of(args[args.length-1]).toAbsolutePath().normalize();
        run(target,flags.contains("--server"),flags.contains("--prepare"),flags.contains("--offline"),System.out::println);
    }
}
