import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.FutureTask;
import javax.swing.*;

final class LocalPlayer {
    static boolean gameOpen(Path game)throws Exception{
        if(ProcessHandle.allProcesses().anyMatch(p->p.info().arguments().map(args->{
            for(int i=1;i<args.length;i++)if(args[i-1].equals("--gameDir"))try{return Files.isSameFile(Path.of(args[i]),game);}catch(Exception ignored){}
            return false;
        }).orElse(false)))return true;
        if(Frontier.MAC)return false;
        var check=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",
            "if(Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object MainWindowTitle -eq 'FRONTIER OF GOD'){exit 10}").start();
        return check.waitFor()==10;
    }
    static String display(Path game)throws Exception{
        Path file=game.resolve(".frontier/local-player.json");
        return Files.isRegularFile(file)?Pack.read(file).get("name").getAsString():"Локальный игрок";
    }
    static void edit(JFrame parent,Path game)throws Exception{
        Path file=game.resolve(".frontier/local-player.json");
        JsonObject old=Files.isRegularFile(file)?Pack.read(file):new JsonObject();
        JsonArray known=known(game);
        String initial=old.has("name")?old.get("name").getAsString():known.size()==1?known.get(0).getAsJsonObject().get("name").getAsString():"";
        var nick=new JTextField(initial,22);
        var about=new JTextField(old.has("about")?old.get("about").getAsString():"",22);
        Object[] fields={"Ник в игре",nick,"О себе (необязательно, до 160 символов)",about};
        if(JOptionPane.showConfirmDialog(parent,fields,"Мой локальный профиль",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        save(game,old,known,nick.getText(),about.getText());
    }
    static JsonArray known(Path game)throws IOException{
        Path cache=game.resolve("usercache.json");
        return Files.isRegularFile(cache)?JsonParser.parseString(Files.readString(cache)).getAsJsonArray():new JsonArray();
    }
    static JsonObject save(Path game,JsonObject p,JsonArray known,String name,String about)throws Exception{
        name=name.strip();about=about.strip();
        if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IOException("Ник: от 1 до 16 латинских букв, цифр или символов подчёркивания.");
        if(about.codePointCount(0,about.length())>160)throw new IOException("Описание должно быть короче 161 символа.");
        UUID uuid=p.has("uuid")?UUID.fromString(p.get("uuid").getAsString()):UUID.randomUUID();
        if(!p.has("uuid")){
            if(known.size()==1)uuid=UUID.fromString(known.get(0).getAsJsonObject().get("uuid").getAsString());
            else for(var e:known){var u=e.getAsJsonObject();if(name.equalsIgnoreCase(u.get("name").getAsString())){uuid=UUID.fromString(u.get("uuid").getAsString());break;}}
        }
        if(!p.has("created"))p.addProperty("created",java.time.Instant.now().toString());
        p.addProperty("name",name);p.addProperty("uuid",uuid.toString());p.addProperty("about",about);
        Pack.write(game.resolve(".frontier/local-player.json"),Pack.JSON.toJson(p));return p;
    }
    static MinecraftAccount.Identity player(Path game)throws Exception{
        Path saved=game.resolve(".frontier/local-player.json");
        JsonObject p;
        if(Files.isRegularFile(saved))p=Pack.read(saved);
        else{
            JsonArray known=new JsonArray();
            Path cache=game.resolve("usercache.json");
            if(Files.isRegularFile(cache))known=JsonParser.parseString(Files.readString(cache)).getAsJsonArray();
            String name;
            if(known.size()==1)name=known.get(0).getAsJsonObject().get("name").getAsString();
            else{
                FutureTask<String> pick=new FutureTask<>(()->JOptionPane.showInputDialog(null,"Ник для одиночной игры. Для прежнего мира укажите прежний ник.","Локальный профиль",JOptionPane.QUESTION_MESSAGE));
                SwingUtilities.invokeLater(pick);name=pick.get();if(name==null)throw new IOException("Запуск отменён.");
            }
            p=save(game,new JsonObject(),known,name,"");
        }
        return new MinecraftAccount.Identity(p.get("name").getAsString(),UUID.fromString(p.get("uuid").getAsString()).toString().replace("-",""),"0","");
    }
}
