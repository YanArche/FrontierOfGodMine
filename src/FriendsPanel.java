import com.google.gson.*;
import java.awt.*;
import java.io.IOException;
import javax.swing.*;

final class FriendsPanel extends JDialog {
    final JPanel entries=Look.clear(new GridLayout(0,1,0,5));
    final JLabel status=Look.label("Синхронизация…",13,Look.MUTED,false);
    final java.util.function.Consumer<String> join;
    boolean busy;
    JsonObject snapshot;
    int selected;
    final JPanel tabs=Look.clear(new GridLayout(2,3,5,4));
    record Section(String label,String key,String primary,String operation,String secondary,String alternate){}
    static final java.util.List<Section> SECTIONS=java.util.List.of(
        new Section("Друзья","friends","Пригласить","invite","Удалить","remove"),
        new Section("Входящие","incoming","Принять","accept","Отклонить","decline"),
        new Section("Исходящие","outgoing","Отменить","cancel",null,null),
        new Section("Приглашения","invites","Подключиться","address","Скрыть","dismiss"),
        new Section("Отправленные","sent","Отозвать","revoke",null,null),
        new Section("Блокировки","blocked","Разблокировать","unblock",null,null));

    FriendsPanel(JFrame owner,java.util.function.Consumer<String> join){
        super(owner,"Друзья WoidWatch",true);this.join=join;
        var scene=new Look.Scene();scene.setBorder(Look.pad(19,20));setContentPane(scene);
        var panel=Wardrobe.glass(new BorderLayout(0,12),235);scene.add(panel);
        var header=Look.clear(new BorderLayout(0,8));panel.add(header,BorderLayout.NORTH);
        header.add(Look.label("Друзья",27,Look.PAPER,true),BorderLayout.NORTH);
        var nick=new JTextField(16);header.add(Look.field("Ник WoidWatch",nick));
        var actions=Look.clear(new FlowLayout(FlowLayout.LEFT,5,0));header.add(actions,BorderLayout.SOUTH);
        button(actions,"Добавить",true,()->send("request",nick.getText().strip()));
        button(actions,"Заблокировать",false,()->send("block",nick.getText().strip()));
        button(actions,"Обновить",false,()->send("view",""));
        var list=Look.clear(new BorderLayout());list.add(entries,BorderLayout.NORTH);
        var content=Look.clear(new BorderLayout(0,9));content.add(tabs,BorderLayout.NORTH);panel.add(content);
        var scroll=new JScrollPane(list);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);content.add(scroll);
        panel.add(status,BorderLayout.SOUTH);
        setSize(660,645);setMinimumSize(new Dimension(600,435));setLocationRelativeTo(owner);
        send("view","");setVisible(true);
    }

    void button(JPanel row,String caption,boolean main,Runnable action){
        var b=Look.button(caption,main);row.add(b);b.addActionListener(e->action.run());
    }

    void send(String op,String target){
        if(busy)return;
        if(!op.equals("view")&&!target.matches("[A-Za-z0-9_]{3,16}")){
            status.setText("Введите ник WoidWatch из 3–16 символов.");return;
        }
        busy=true;status.setText("Синхронизация…");
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{
                boolean test=AccountPanel.test();var body=new JsonObject();body.addProperty("op",op);body.addProperty("target",target);
                var result=AccountPanel.api(test,"/desktop/social",body,AccountPanel.token(test));
                if(result==null)throw new IOException("Войдите в аккаунт WoidWatch.");return result;
            }
            protected void done(){
                busy=false;
                try{snapshot=get();draw();status.setText(op.equals("invite")?"Приглашение отправлено на 24 часа.":"Список обновлён.");}
                catch(Exception e){status.setText(e.getCause()==null?e.getMessage():e.getCause().getMessage());}
            }
        }.execute();
    }

    void draw(){
        entries.removeAll();
        tabs.removeAll();
        for(int i=0;i<SECTIONS.size();i++){
            int index=i;var tab=SECTIONS.get(i);int count=snapshot.getAsJsonArray(tab.key()).size();
            button(tabs,tab.label()+(count==0?"":" · "+count),selected==i,()->{selected=index;draw();});
        }
        var tab=SECTIONS.get(selected);
        section(tab.label(),tab.key(),tab.primary(),tab.operation(),tab.secondary(),tab.alternate());
        if(entries.getComponentCount()==0)entries.add(Look.label(selected==0?"Добавьте друга по нику WoidWatch.":"В этой вкладке пока пусто.",14,Look.MUTED,false));
        tabs.revalidate();tabs.repaint();
        entries.revalidate();entries.repaint();
    }

    void section(String label,String key,String primary,String first,String secondary,String second){
        var values=snapshot.getAsJsonArray(key);if(values.isEmpty())return;
        entries.add(Look.label(label,13,Look.MINT,true));
        for(var entry:values){
            String name=entry.getAsString();
            var row=Wardrobe.glass(new BorderLayout(6,0),140);entries.add(row);
            row.add(Look.label(name,14,Look.PAPER,true),BorderLayout.WEST);
            var actions=Look.clear(new FlowLayout(FlowLayout.RIGHT,6,0));row.add(actions,BorderLayout.EAST);
            button(actions,primary,false,()->{if(first.equals("address"))joinServer();else send(first,name);});
            if(secondary!=null)button(actions,secondary,false,()->send(second,name));
        }
    }

    void joinServer(){
        if(busy)return;
        busy=true;status.setText("Проверка адреса сообщества…");
        new SwingWorker<String,Void>(){
            protected String doInBackground()throws Exception{return Community.refresh();}
            protected void done(){
                busy=false;
                try{
                    String address=get();
                    if(address.isEmpty()){status.setText("Адрес сервера сообщества ещё не задан.");return;}
                    dispose();join.accept(address);
                }catch(Exception e){status.setText("Не удалось получить адрес сервера сообщества.");}
            }
        }.execute();
    }
}
