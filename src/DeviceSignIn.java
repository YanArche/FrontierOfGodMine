import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Desktop;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.*;

final class DeviceSignIn {
    static JsonObject request(String path,String form)throws Exception{
        var http=HttpRequest.newBuilder(URI.create(MinecraftAccount.OAUTH+path)).timeout(Duration.ofSeconds(30))
            .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build();
        var response=WoidAccount.HTTP.send(http,HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200&&response.statusCode()!=400)throw new IOException("Microsoft: HTTP "+response.statusCode());
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }
    static JsonObject login(String id)throws Exception{
        var challenge=request("devicecode","client_id="+id+"&scope="+MinecraftAccount.enc("XboxLive.signin offline_access"));
        if(!challenge.has("device_code"))throw new IOException("Microsoft не разрешил вход для этого приложения.");
        String code=challenge.get("user_code").getAsString();
        URI uri=URI.create(challenge.get("verification_uri").getAsString());
        if(!"https".equals(uri.getScheme())||!java.util.Set.of("www.microsoft.com","microsoft.com","login.microsoftonline.com").contains(uri.getHost()))throw new IOException("Неожиданный адрес страницы Microsoft");
        var cancelled=new AtomicBoolean();JDialog[] view=new JDialog[1];
        SwingUtilities.invokeAndWait(()->{
            var dialog=new JDialog(java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow(),"WoidWatch · Вход в Minecraft");
            view[0]=dialog;dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            dialog.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){cancelled.set(true);}});
            JPanel p=new JPanel();p.setBorder(BorderFactory.createEmptyBorder(18,24,18,24));p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
            p.add(new JLabel("Подтвердите вход в Microsoft на этом устройстве или телефоне."));
            p.add(new JLabel(uri.toASCIIString()));JTextField codeField=new JTextField(code);codeField.setEditable(false);
            codeField.setFont(codeField.getFont().deriveFont(28f));p.add(codeField);
            JButton open=new JButton("Открыть страницу Microsoft");p.add(open);open.addActionListener(e->{try{Desktop.getDesktop().browse(uri);}catch(Exception ignored){codeField.selectAll();}});
            JButton stop=new JButton("Отменить вход");p.add(stop);stop.addActionListener(e->dialog.dispose());
            dialog.add(p);dialog.pack();dialog.setLocationRelativeTo(dialog.getOwner());dialog.setVisible(true);
        });
        long until=System.nanoTime()+Math.min(1800,challenge.get("expires_in").getAsInt())*1_000_000_000L;
        int delay=challenge.has("interval")?Math.max(1,challenge.get("interval").getAsInt()):5;
        try{
            while(System.nanoTime()<until){
                for(int i=0;i<delay*5;i++){if(cancelled.get())throw new IOException("Вход отменён");Thread.sleep(200);}
                var reply=request("token","grant_type=urn:ietf:params:oauth:grant-type:device_code&client_id="+id+"&device_code="+MinecraftAccount.enc(challenge.get("device_code").getAsString()));
                if(cancelled.get())throw new IOException("Вход отменён");
                if(reply.has("access_token"))return reply;
                switch(reply.get("error").getAsString()){
                    case "authorization_pending" -> {}
                    case "slow_down" -> delay+=5;
                    case "authorization_declined","access_denied" -> throw new IOException("Вход отклонён");
                    case "expired_token" -> throw new IOException("Срок действия кода истёк");
                    default -> throw new IOException("Microsoft не подтвердил вход");
                }
            }
            throw new IOException("Срок действия кода истёк");
        }finally{SwingUtilities.invokeLater(()->view[0].dispose());}
    }
}
