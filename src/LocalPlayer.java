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
            name=name.strip();if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IOException("Ник должен содержать от 1 до 16 латинских букв, цифр или символов подчёркивания.");
            UUID uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            for(var e:known){var user=e.getAsJsonObject();if(name.equalsIgnoreCase(user.get("name").getAsString())){name=user.get("name").getAsString();uuid=UUID.fromString(user.get("uuid").getAsString());break;}}
            p=new JsonObject();p.addProperty("name",name);p.addProperty("uuid",uuid.toString());Pack.write(saved,Pack.JSON.toJson(p));
        }
        return new MinecraftAccount.Identity(p.get("name").getAsString(),UUID.fromString(p.get("uuid").getAsString()).toString().replace("-",""),"0","");
    }
}
