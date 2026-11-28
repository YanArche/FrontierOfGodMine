import com.google.gson.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import ww.Choice;

final class Wardrobe extends JDialog {
    final JPanel rows=new JPanel(new GridLayout(0,1,0,4));
    final JLabel note=Look.label("Загрузка…",13,Look.PAPER,false);
    final Runnable refresh; boolean shop,busy; JsonObject state;
    Wardrobe(JFrame owner,Runnable refresh){
        super(owner,"Гардероб WoidWatch",true);this.refresh=refresh;
        var panel=new JPanel(new BorderLayout(8,8));panel.setBackground(Look.INK);panel.setBorder(Look.pad(14,18));setContentPane(panel);
        var tabs=Look.clear(new FlowLayout());panel.add(tabs,BorderLayout.NORTH);
        for(String tab:java.util.List.of("Инвентарь","Магазин")){
            var btn=Look.button(tab,false);tabs.add(btn);btn.addActionListener(e->{shop=tab.equals("Магазин");draw();});
        }
        rows.setOpaque(false);panel.add(rows);panel.add(note,BorderLayout.SOUTH);setSize(420,550);setLocationRelativeTo(owner);
        send(new JsonObject());setVisible(true);
    }
    void send(JsonObject body){
        if(busy)return;busy=true;note.setText("Синхронизация…");
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground() throws Exception {
                boolean test=AccountPanel.test();var result=AccountPanel.api(test,"/desktop/wardrobe",body,AccountPanel.token(test));
                if(result==null)throw new IOException("Войдите в аккаунт WoidWatch.");return result;
            }
            protected void done(){busy=false;try{state=get();note.setText("Гардероб синхронизирован");draw();if(body.size()>0)refresh.run();}
                catch(Exception ex){note.setText(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage());}}
        }.execute();
    }
    void draw(){rows.removeAll();if(state!=null){
        if(!shop){
            var upload=Look.button("Загрузить свой скин PNG…",false);rows.add(upload);
            upload.addActionListener(e->{var picker=new JFileChooser();if(picker.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
                try{var img=Avatar.read(picker.getSelectedFile().toPath());if(img==null||img.getHeight()!=64)throw new IOException("Выберите PNG Minecraft 64×64.");
                    var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(img,"png",out);skin(Base64.getEncoder().encodeToString(out.toByteArray()));
                }catch(Exception ex){note.setText(ex.getMessage());}});
            var reset=Look.button("Вернуть скин Стива",false);rows.add(reset);reset.addActionListener(e->skin(""));
        }
        for(var choice:Choice.rows(state,shop)){var button=Look.button(choice.label(),false);rows.add(button);button.setEnabled(!choice.op().isEmpty());button.addActionListener(e->send(choice.request()));}
    }rows.revalidate();rows.repaint();}
    void skin(String value){var body=new JsonObject();body.addProperty("op","skin");body.addProperty("skin",value);send(body);}
}
