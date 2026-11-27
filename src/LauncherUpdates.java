import com.google.gson.*;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;

final class LauncherUpdates {
    static final int BUILD = 1;
    static final String VERSION = "1.1.0";
    static final String CHANNEL = "https://raw.githubusercontent.com/"+Pack.PROJECT+"/main/launcher/stable.json";
    static Path home(){return Frontier.game().getParent().resolve("woidwatch");}
    record Release(int build,String version,String hash,long bytes) {}
    static Release verify(JsonObject envelope)throws Exception{
        byte[] data=Base64.getDecoder().decode(envelope.get("manifest").getAsString());
        Signature sig=Signature.getInstance("SHA256withRSA");
        try(var in=LauncherUpdates.class.getResourceAsStream("/release.cer")){
            sig.initVerify(CertificateFactory.getInstance("X.509").generateCertificate(in));
        }
        sig.update(data);
        if(!sig.verify(Base64.getDecoder().decode(envelope.get("signature").getAsString())))throw new IOException("Подпись обновления недействительна");
        JsonObject m=JsonParser.parseString(new String(data,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        var r=new Release(m.get("build").getAsInt(),m.get("version").getAsString(),m.get("sha256").getAsString(),m.get("bytes").getAsLong());
        if(!m.get("product").getAsString().equals("woidwatch-launcher")||!r.version.matches("[0-9]+\\.[0-9]+\\.[0-9]+")||!r.hash.matches("[a-f0-9]{64}")||r.bytes<1||r.bytes>33554432||r.build<1)throw new IOException("Недопустимый манифест");
        return r;
    }
    static Path jar(Path root, Release r){return root.resolve(r.hash+".jar");}
    static void checkFile(Path file,Release r)throws Exception{
        if(Files.size(file)!=r.bytes||!Pack.digest(file).equals(r.hash))throw new IOException("Повреждён пакет обновления");
    }
    static void stage(Path root,int floor)throws Exception{
        Path meta=Files.createTempFile(root,"manifest-",".tmp"),tmp=Files.createTempFile(root,"download-",".tmp");
        try{
            Pack.get(CHANNEL,meta);
            Release r=verify(Pack.read(meta));
            if(r.build<=floor)return;
            String url="https://github.com/"+Pack.PROJECT+"/releases/download/launcher-v"+r.version+"/woidwatch.jar";
            Pack.get(url,tmp);checkFile(tmp,r);Pack.move(tmp,jar(root,r));Pack.move(meta,root.resolve("pending.json"));
        }finally{Files.deleteIfExists(meta);Files.deleteIfExists(tmp);}
    }
    static Path select(Path root,JsonObject envelope)throws Exception{
        if(envelope==null)return Frontier.resources.resolve("frontier.jar");
        Release r=verify(envelope);Path file=jar(root,r);checkFile(file,r);return file;
    }
    static Process spawn(Path file,Path ready)throws IOException{
        String cp=file+File.pathSeparator+Frontier.resources.resolve("gson.jar");
        return new ProcessBuilder(Frontier.java().toString(),"-Dfrontier.resources="+Frontier.resources,"-cp",cp,"Frontier","--library",ready.toString()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(home().resolve("application.log").toFile())).start();
    }
    static boolean healthy(Process p,Path ready)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(45);
        while(p.isAlive()&&!Files.exists(ready)&&System.nanoTime()<end)Thread.sleep(100);
        if(!Files.exists(ready)){p.destroyForcibly().waitFor();return false;}
        return true;
    }
    static void promote(Path root,JsonObject state)throws Exception{
        Path pending=root.resolve("pending.json");
        if(!Files.isRegularFile(pending))return;
        try{
            JsonObject candidate=Pack.read(pending);Release r=verify(candidate);
            int floor=Math.max(BUILD,state.has("highest")?state.get("highest").getAsInt():0);
            if(r.build>floor){
                select(root,candidate);
                JsonObject active=state.getAsJsonObject("active");
                if(active!=null)state.add("previous",active);else state.remove("previous");
                state.add("active",candidate);state.addProperty("highest",r.build);
                Pack.write(root.resolve("active.json"),Pack.JSON.toJson(state));
            }
        }catch(Exception e){Pack.write(root.resolve("update-status.txt"),"Обновление отклонено: "+e.getMessage());}
        Files.deleteIfExists(pending);
    }
    static void run()throws Exception{
        Path root=home();Files.createDirectories(root);
        try(var channel=FileChannel.open(root.resolve("library.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()){
            if(lock==null){JOptionPane.showMessageDialog(null,"Библиотека уже открыта.");return;}
            Path state=root.resolve("active.json"),pending=root.resolve("pending.json"),ready=root.resolve("ready");
            JsonObject s=Files.exists(state)?Pack.read(state):new JsonObject();
            JsonObject active=s.getAsJsonObject("active"),previous=s.getAsJsonObject("previous");
            int floor=Math.max(BUILD,s.has("highest")?s.get("highest").getAsInt():0);
            promote(root,s);
            active=s.getAsJsonObject("active");previous=s.getAsJsonObject("previous");
            floor=Math.max(floor,s.has("highest")?s.get("highest").getAsInt():0);
            final int highest=floor;
            Thread worker=new Thread(()->{try{stage(root,highest);Pack.write(root.resolve("update-status.txt"),Files.exists(pending)?"Обновление готово к следующему запуску":"Установлена актуальная версия");}
                catch(Exception e){try{Pack.write(root.resolve("update-status.txt"),"Ошибка проверки: "+e.getMessage());}catch(IOException ignored){}}});
            worker.start();
            try{
                while(true){
                    Files.deleteIfExists(ready);
                    try{
                        Process p=spawn(select(root,active),ready);
                        if(!healthy(p,ready)||p.waitFor()!=0)throw new IOException("Ошибка запуска библиотеки");
                        break;
                    }catch(Exception failure){
                        if(active==null)throw failure;
                        active=previous;previous=null;s.remove("previous");s.remove("active");if(active!=null)s.add("active",active);
                        Pack.write(state,Pack.JSON.toJson(s));Pack.write(root.resolve("rollback.txt"),"Возвращена предыдущая версия лаунчера: "+failure.getMessage());
                    }
                }
            }finally{worker.join();promote(root,s);}
        }
    }
}
