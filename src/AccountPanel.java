import com.google.gson.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import javax.swing.*;

final class AccountPanel {
    static final Path MODE=LauncherUpdates.home().resolve("account-environment");
    static boolean test()throws IOException{return Files.exists(MODE)&&Files.readString(MODE).equals("test");}
    static Path cache(boolean test){return LauncherUpdates.home().resolve(test?"test-user.session":"server-user.session");}
    static String slot(boolean test){return test?"account-test":"account-server";}
    static JsonObject api(boolean test,String path,JsonObject body,String token)throws Exception{
        URI url=URI.create((test?"http://127.0.0.1:18787":WoidAccount.ORIGIN)+path);
        var b=HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(12));
        if(body!=null)b.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if(token!=null)b.header("Authorization","Bearer "+token);
        HttpResponse<String> r;
        try{r=WoidAccount.HTTP.send(b.build(),HttpResponse.BodyHandlers.ofString());}
        catch(IOException ex){throw new IOException(test?"Запустите Test-accounts.cmd на этом ПК.":"Сервер аккаунтов недоступен.",ex);}
        if(r.statusCode()==401)return null;
        if(r.statusCode()!=200)throw new IOException(switch(r.statusCode()){
            case 400 -> "Проверьте ник и пароль (для регистрации от 12 до 128 символов).";
            case 403 -> "Приглашение недействительно или уже использовано.";
            case 409 -> "Ник уже занят.";
            case 429 -> "Слишком много попыток. Повторите через пять минут.";
            case 503 -> "Регистрация и вход доступны после загрузки сервера.";
            default -> "Сервис аккаунтов недоступен (HTTP "+r.statusCode()+").";});
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }
    static String token(boolean test)throws Exception{
        return Files.exists(cache(test))?WoidAccount.protect(Files.readString(cache(test)),false,slot(test)):null;
    }
    static JsonObject current(boolean test)throws Exception{
        String t=token(test);if(t==null)return null;
        var me=api(test,"/me",null,t);if(me==null)Files.deleteIfExists(cache(test));return me;
    }
    static MinecraftAccount.Identity identity(Path game)throws Exception{
        boolean test=test();var me=current(test);
        if(me==null)throw new IOException("Откройте «Аккаунт» и войдите в WoidWatch.");
        String name=me.get("name").getAsString(),id=UUID.fromString(me.get("id").getAsString()).toString();
        Path file=game.resolve(".frontier/accounts").resolve((test?"test-":"")+id+".uuid");
        if(!Files.exists(file)){
            String uuid=id;
            Path local=game.resolve(".frontier/local-player.json");
            if(!test&&Files.exists(local)){var old=Pack.read(local);if(name.equalsIgnoreCase(old.get("name").getAsString()))uuid=UUID.fromString(old.get("uuid").getAsString()).toString();}
            Pack.write(file,uuid);
        }
        return new MinecraftAccount.Identity(name,UUID.fromString(Files.readString(file)).toString().replace("-",""),"0","");
    }
    static void open(JFrame parent,JLabel status)throws Exception{
        JDialog d=new JDialog(parent,"Аккаунт WoidWatch",true);JPanel p=new JPanel(new GridLayout(0,1,4,4));
        p.setBorder(BorderFactory.createEmptyBorder(14,20,14,20));
        JCheckBox sandbox=new JCheckBox("Тестовый сервер на этом ПК",test());p.add(Look.label("Профиль WoidWatch",23,Look.PAPER,true));p.add(sandbox);
        JTextField name=new JTextField(22),invite=new JTextField();JPasswordField pass=new JPasswordField();
        p.add(new JLabel("Ник (3–16 букв A–Z, цифры и _)"));p.add(name);p.add(new JLabel("Пароль"));p.add(pass);p.add(new JLabel("Приглашение для регистрации"));p.add(invite);
        JLabel message=new JLabel(" ");p.add(message);JPanel buttons=new JPanel();
        for(String action:new String[]{"Войти","Регистрация","Выйти"}){
            JButton button=Look.button(action,action.equals("Войти"));buttons.add(button);button.addActionListener(e->{
                boolean test=sandbox.isSelected();char[] pw=pass.getPassword();var body=new JsonObject();
                body.addProperty("name",name.getText().strip());body.addProperty("invite",invite.getText().strip());body.addProperty("password",new String(pw));Arrays.fill(pw,'\0');pass.setText("");
                for(Component c:buttons.getComponents())c.setEnabled(false);sandbox.setEnabled(false);d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
                new Thread(()->{try{
                    String note;
                    if(action.equals("Выйти")){String t=token(test);if(t!=null)api(test,"/desktop/logout",new JsonObject(),t);Files.deleteIfExists(cache(test));Pack.write(MODE,test?"test":"production");note="Вы вышли из аккаунта.";}
                    else{
                        var user=api(test,action.equals("Войти")?"/desktop/login":"/desktop/register",body,null);if(user==null)throw new IOException("Неверный ник или пароль.");
                        String t=user.get("token").getAsString();String sealed=WoidAccount.protect(t,true,slot(test));Pack.write(cache(test),Frontier.MAC?"keychain":sealed);
                        Pack.write(MODE,test?"test":"production");note=(test?"Тестовый аккаунт: ":"Аккаунт: ")+user.get("name").getAsString();
                    }
                    SwingUtilities.invokeLater(()->{status.setText(note);d.dispose();});
                }catch(Exception ex){SwingUtilities.invokeLater(()->message.setText(ex.getMessage()));}
                finally{SwingUtilities.invokeLater(()->{for(Component c:buttons.getComponents())c.setEnabled(true);sandbox.setEnabled(true);d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);});}}).start();
            });
        }
        p.add(buttons);d.setContentPane(p);d.pack();d.setMinimumSize(new Dimension(440,d.getHeight()));d.setLocationRelativeTo(parent);d.setVisible(true);
    }
}
