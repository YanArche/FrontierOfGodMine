import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.*;

/** Run the unmodified Prism application using its documented instance launcher. */
final class PrismGame {
    static final String VERSION="11.1.1";
    static final String WIN_HASH="ab35a770fb06d89d2ccc098079db5db329fb4e68f42b72babd8b095efde3d2d7";
    static final String MAC_HASH="15126a7df57acb4f37fe581dad68cb03c7bb60b70eb82345f8bb3246235600c6";
    static Path home(){return LauncherUpdates.home().resolve("prism");}
    static Path binary(Path dir)throws IOException{
        String name=Frontier.MAC?"prismlauncher":"prismlauncher.exe";
        try(var entries=Files.find(dir,6,(p,a)->a.isRegularFile()&&p.getFileName().toString().equalsIgnoreCase(name))){
            return entries.findFirst().orElseThrow(()->new IOException("Программа Prism не найдена в дистрибутиве."));
        }
    }
    static Path install(Consumer<String> log)throws Exception{
        Path root=home();Files.createDirectories(root);
        Path target=root.resolve("prism-"+VERSION);
        if(Files.isDirectory(target))return binary(target);
        log.accept("Первая установка Prism Launcher…");
        String archive="PrismLauncher-"+(Frontier.MAC?"macOS":"Windows-MSVC-Portable")+"-"+VERSION+".zip";
        Path zip=Files.createTempFile(root,"prism-", ".zip"),stage=Files.createTempDirectory(root,"install-");
        try{
            Pack.get("https://github.com/PrismLauncher/PrismLauncher/releases/download/"+VERSION+"/"+archive,zip);
            if(!Pack.digest(zip).equals(Frontier.MAC?MAC_HASH:WIN_HASH))throw new IOException("Контрольная сумма Prism не совпала.");
            if(Frontier.MAC){
                if(new ProcessBuilder("/usr/bin/ditto","-x","-k",zip.toString(),stage.toString()).start().waitFor()!=0)throw new IOException("Ошибка распаковки Prism");
            }else try(ZipInputStream entries=new ZipInputStream(Files.newInputStream(zip))){
                for(ZipEntry e;(e=entries.getNextEntry())!=null;){
                    Path file=stage.resolve(e.getName()).normalize();if(!file.startsWith(stage))throw new IOException("Недопустимый путь в архиве");
                    if(e.isDirectory())Files.createDirectories(file);else{Files.createDirectories(file.getParent());Files.copy(entries,file);}
                }
            }
            binary(stage);Files.move(stage,target);return binary(target);
        }finally{Files.deleteIfExists(zip);if(Files.exists(stage))Pack.clean(stage);}
    }
    static String instance(Path game)throws Exception{
        Path real=game.toRealPath();String canonical=real.toString();if(!Frontier.MAC)canonical=canonical.toLowerCase(Locale.ROOT);
        String suffix=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0,12);
        String id="WoidWatch-Frontier-"+suffix;
        Path dir=home().resolve("data/instances").resolve(id);Files.createDirectories(dir);
        Path link=dir.resolve("minecraft");
        if(!Files.exists(link,LinkOption.NOFOLLOW_LINKS)){
            if(Frontier.MAC)Files.createSymbolicLink(link,real);
            else{
                var pb=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command","$ErrorActionPreference='Stop';New-Item -ItemType Junction -Path $env:WW_INSTANCE_LINK -Target $env:WW_GAME_PATH | Out-Null");
                pb.environment().put("WW_INSTANCE_LINK",link.toString());pb.environment().put("WW_GAME_PATH",real.toString());
                if(pb.start().waitFor()!=0)throw new IOException("Не удалось подключить папку игры к Prism.");
            }
        }
        if(!Files.isSameFile(link,real))throw new IOException("Папка профиля Prism уже занята.");
        var components=new JsonArray();
        for(var pair:List.of(new String[]{"net.minecraft","1.21.1"},new String[]{"net.neoforged","21.1.267"})){
            var c=new JsonObject();c.addProperty("uid",pair[0]);c.addProperty("version",pair[1]);components.add(c);
        }
        var pack=new JsonObject();pack.addProperty("formatVersion",1);pack.add("components",components);
        Pack.write(dir.resolve("mmc-pack.json"),Pack.JSON.toJson(pack));
        Path config=dir.resolve("instance.cfg");
        if(!Files.exists(config))Pack.write(config,"[General]\nInstanceType=OneSix\nname=Frontier of God\nOverrideMemory=true\nMinMemAlloc=512\nMaxMemAlloc=6144\n");
        return id;
    }
    static void launch(Path game,Consumer<String> log)throws Exception{
        Path executable=install(log);String id=instance(game);
        log.accept("Запуск Фронтира. При первом входе выберите аккаунт Microsoft в Prism.");
        var command=new ArrayList<String>();
        if(Frontier.MAC){Path bundle=executable;while(bundle!=null&&!bundle.toString().endsWith(".app"))bundle=bundle.getParent();
            if(bundle==null)throw new IOException("Пакет Prism повреждён");
            Collections.addAll(command,"/usr/bin/open","-a",bundle.toString(),"--args");
        }else command.add(executable.toString());
        Collections.addAll(command,"--dir",home().resolve("data").toString(),"--launch",id);
        new ProcessBuilder(command).directory(executable.getParent().toFile()).start();
    }
}
