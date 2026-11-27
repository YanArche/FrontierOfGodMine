import com.google.gson.*;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import javax.swing.*;

final class WoidAccount {
    static final String ORIGIN="https://accounts.noboredgames.win";
    static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    static JsonObject request(String path,JsonObject body,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(ORIGIN+path)).timeout(Duration.ofSeconds(10));
        if(body!=null)b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if(token!=null)b.header("Authorization","Bearer "+token);
        var response=HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==401)return null;
        if(response.statusCode()!=200)throw new IOException("Сервис аккаунтов: HTTP "+response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    static String nativeCommand(ProcessBuilder pb,String input)throws Exception{
        var process=pb.redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try(var out=process.getOutputStream()){out.write(input.getBytes(StandardCharsets.UTF_8));}
        String result=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8).trim();
        if(process.waitFor()!=0)throw new IOException("Хранилище ключей ОС недоступно");return result;
    }
    static String protect(String value,boolean save)throws Exception{
        return protect(value,save,"community");
    }
    static String protect(String value,boolean save,String slot)throws Exception{
        if(!slot.matches("[a-z0-9_-]{1,32}"))throw new IOException("Недопустимый ключ");
        String service="win.noboredgames.woidwatch."+slot;
        if(Frontier.MAC){
            if(save){
                String encoded=java.util.Base64.getUrlEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
                nativeCommand(new ProcessBuilder("/usr/bin/security","-i"),"add-generic-password -U -a WoidWatch -s "+service+" -w "+encoded+"\n");
                return value;
            }
            String encoded=nativeCommand(new ProcessBuilder("/usr/bin/security","find-generic-password","-a","WoidWatch","-s",service,"-w"),"");
            return new String(java.util.Base64.getUrlDecoder().decode(encoded),StandardCharsets.UTF_8);
        }
        String call=save?"ConvertFrom-SecureString (ConvertTo-SecureString $v -AsPlainText -Force)":"[Net.NetworkCredential]::new('',(ConvertTo-SecureString $v)).Password";
        return nativeCommand(new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command","$ErrorActionPreference='Stop';$env:PSModulePath=$PSHOME+'/Modules';$v=[Console]::In.ReadToEnd();"+call),value);
    }
    static Path cache(){return LauncherUpdates.home().resolve("account.session");}
    static JsonObject current()throws Exception{
        if(!Files.exists(cache()))return null;
        String token=protect(Files.readString(cache()),false);JsonObject me=request("/me",null,token);
        if(me==null)Files.deleteIfExists(cache());return me;
    }
    static void login(JFrame frame,JButton button,JLabel status){
        button.setEnabled(false);new Thread(()->{try{
            JsonObject me=current();
            if(me==null){
                JsonObject pair=request("/desktop/start",new JsonObject(),null);
                String code=pair.get("code").getAsString(),browser=pair.get("browser").getAsString();
                if(!browser.matches("[A-Za-z0-9_-]{43}")||!code.matches("[0-9]{6}"))throw new IOException("Некорректный ответ сервиса");
                Desktop.getDesktop().browse(URI.create(ORIGIN+"/#device="+browser));
                SwingUtilities.invokeLater(()->status.setText("Введите в браузере код этого лаунчера: "+code));
                var query=new JsonObject();query.add("secret",pair.get("secret"));
                for(int i=0;i<110;i++){
                    Thread.sleep(2500);var result=request("/desktop/poll",query,null);if(result.has("pending"))continue;
                    String token=result.get("token").getAsString();if(!token.matches("[A-Za-z0-9_-]{43}"))throw new IOException("Некорректная сессия");
                    String encrypted=protect(token,true);Pack.write(cache(),Frontier.MAC?"keychain":encrypted);me=result;break;
                }
                if(me==null)throw new IOException("Срок подтверждения истёк.");
            }
            String name=me.get("name").getAsString();SwingUtilities.invokeLater(()->status.setText("Аккаунт WoidWatch: "+name));
        }catch(Exception ex){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,"Вход недоступен. Проверьте подключение к "+ORIGIN+"\n"+ex.getClass().getSimpleName(),"WoidWatch",JOptionPane.INFORMATION_MESSAGE));}
        finally{SwingUtilities.invokeLater(()->button.setEnabled(true));}}).start();
    }
}
