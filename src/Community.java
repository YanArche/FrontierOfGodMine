import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Base64;
import javax.swing.*;

final class Community {
    static final String REPO="https://api.github.com/repos/"+Pack.PROJECT;
    static final String FILE=REPO+"/contents/launcher/community.json";
    static final String RAW="https://raw.githubusercontent.com/"+Pack.PROJECT+"/main/launcher/community.json";
    static String address(String input){
        String value=input.strip();if(value.isEmpty())return "";
        try{
            URI uri=URI.create("tcp://"+value);String host=uri.getHost();
            if(host==null||uri.getUserInfo()!=null||!uri.getPath().isEmpty()||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()>65535||uri.getPort()==0||value.endsWith(":"))throw new Exception();
            return value;
        }catch(Exception e){throw new IllegalArgumentException("Введите DNS-имя, IPv4 или [IPv6] с необязательным портом 1–65535.");}
    }
    static JsonObject get(String url,String token)throws Exception{return api(url,token,null);}
    static JsonObject api(String url,String token,JsonObject body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12)).header("User-Agent","WoidWatch");
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body!=null)b.header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString(body.toString()));
        var response=HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()/100!=2)throw new IOException("GitHub HTTP "+response.statusCode()+". Проверьте подключение и права на репозиторий.");
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    static String refresh()throws Exception{
        Path cache=LauncherUpdates.home().resolve("community.json");
        try{var cfg=get(RAW,null);String a=address(cfg.get("address").getAsString());Pack.write(cache,cfg.toString());return a;}
        catch(Exception e){if(Files.exists(cache))return address(Pack.read(cache).get("address").getAsString());throw e;}
    }
    static String ownerToken()throws Exception{
        var pb=new ProcessBuilder("git","-c","credential.interactive=false","credential","fill").redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.environment().put("GIT_TERMINAL_PROMPT","0");pb.environment().put("GCM_INTERACTIVE","never");
        Process p=pb.start();try(var out=p.getOutputStream()){out.write("protocol=https\nhost=github.com\n\n".getBytes(StandardCharsets.UTF_8));}
        if(!p.waitFor(12,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Истекло время проверки Git.");}
        String token=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).lines().filter(s->s.startsWith("password=")).map(s->s.substring(9)).findFirst().orElseThrow(()->new IOException("Войдите в Git Credential Manager под владельцем репозитория."));
        if(!get("https://api.github.com/user",token).get("id").equals(get(REPO,token).getAsJsonObject("owner").get("id")))throw new IOException("Доступ только владельцу репозитория.");
        return token;
    }
    static void admin(JFrame frame,JButton button,JLabel label){
        button.setEnabled(false);new Thread(()->{try{
            String token=ownerToken();var file=get(FILE+"?ref=main",token);
            var cfg=JsonParser.parseString(new String(Base64.getMimeDecoder().decode(file.get("content").getAsString()),StandardCharsets.UTF_8)).getAsJsonObject();
            String[] answer={null};SwingUtilities.invokeAndWait(()->answer[0]=JOptionPane.showInputDialog(frame,"Публичный адрес игрового сервера. Пустое поле снимает адрес.",cfg.get("address").getAsString()));
            if(answer[0]==null)return;
            cfg.addProperty("address",address(answer[0]));var body=new JsonObject();body.addProperty("message","Обновить адрес сервера WoidWatch");body.addProperty("branch","main");body.add("sha",file.get("sha"));
            body.addProperty("content",Base64.getEncoder().encodeToString(cfg.toString().getBytes(StandardCharsets.UTF_8)));api(FILE,token,body);
            SwingUtilities.invokeLater(()->label.setText("Адрес опубликован. Игроки получат его перед запуском."));
        }catch(Exception e){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,e.getMessage(),"Сервер WoidWatch",JOptionPane.ERROR_MESSAGE));}
        finally{SwingUtilities.invokeLater(()->button.setEnabled(true));}}).start();
    }
}
