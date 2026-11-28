import com.google.gson.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.plaf.basic.BasicSliderUI;

final class Wardrobe extends JDialog {
    record Sample(String id,String name,String slot){}
    static final Sample[] SAMPLES={
        new Sample("hood","Дорожный капюшон","hat"),new Sample("mushroom","Грибная шляпа","hat"),new Sample("top_hat","Цилиндр","hat"),
        new Sample("cape_1","Янтарный плащ","cape"),new Sample("cape_2","Бирюзовый плащ","cape"),
        new Sample("cape_3","Плащ ночного неба","cape"),new Sample("cape_4","Алый плащ","cape")
    };
    final Map<String,BufferedImage> artwork=new HashMap<>();
    final Map<String,String> fitting=new HashMap<>();
    final JPanel rows=Look.clear(new GridLayout(0,1,0,6)),tabs=Look.clear(new GridLayout(2,3,5,4));
    final JLabel note=Look.label("Загрузка гардероба…",13,Look.PAPER,false);
    final Avatar avatar=new Avatar();
    final Runnable refresh;
    JsonObject state;
    boolean busy,store;
    String category="skin",skin="";
    BufferedImage steve;

    Wardrobe(JFrame owner,Runnable refresh){
        super(owner,"Гардероб WoidWatch",true);
        this.refresh=refresh;
        for(var item:SAMPLES)artwork.put(item.id(),Look.art("wardrobe/"+item.id()+".png"));
        var scene=new Look.Scene();scene.setBorder(Look.pad(18,20));setContentPane(scene);
        var header=Look.clear(new BorderLayout(8,7));scene.add(header,BorderLayout.NORTH);
        header.add(Look.label("Гардероб",27,Look.PAPER,true),BorderLayout.WEST);
        var mode=Look.clear(new FlowLayout(FlowLayout.RIGHT,6,0));header.add(mode,BorderLayout.EAST);
        var inventory=Look.button("Примерочная",false);inventory.setForeground(Look.MINT);var shop=Look.button("Магазин",false);
        mode.add(inventory);mode.add(shop);
        inventory.addActionListener(e->{store=false;category="skin";inventory.setForeground(Look.MINT);shop.setForeground(Look.PAPER);draw();});
        shop.addActionListener(e->{store=true;category="hat";inventory.setForeground(Look.PAPER);shop.setForeground(Look.MINT);draw();});

        var body=Look.clear(new GridLayout(1,2,16,0));body.setBorder(Look.pad(14,0));scene.add(body);
        var stage=glass(new BorderLayout(4,8),195);stage.add(Look.label("ВИД ПЕРСОНАЖА",12,Look.MINT,true),BorderLayout.NORTH);
        avatar.studio=true;avatar.follow=false;stage.add(avatar);
        var sliders=Look.clear(new GridLayout(0,1,0,2));
        sliders.add(slider("Поворот",-180,180,0,n->{avatar.turn=Math.toRadians(n);avatar.repaint();}));
        sliders.add(slider("Масштаб",70,125,100,n->{avatar.zoom=n/100.;avatar.repaint();}));
        stage.add(sliders,BorderLayout.SOUTH);body.add(stage);
        var catalog=glass(new BorderLayout(4,8),226);body.add(catalog);catalog.add(tabs,BorderLayout.NORTH);
        var list=Look.clear(new BorderLayout());list.add(rows,BorderLayout.NORTH);
        var scroll=new JScrollPane(list);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        catalog.add(scroll);
        var footer=glass(new BorderLayout(7,8),236);scene.add(footer,BorderLayout.SOUTH);footer.add(note,BorderLayout.NORTH);
        var actions=Look.clear(new FlowLayout(FlowLayout.RIGHT,6,0));footer.add(actions,BorderLayout.EAST);
        var reset=Look.button("Отменить",false);var save=Look.button("Сохранить образ",true);
        actions.add(reset);actions.add(save);
        reset.addActionListener(e->{if(state!=null&&!busy){restore();draw();note.setText("Восстановлен сохранённый образ.");}});
        save.addActionListener(e->save());
        setSize(1000,690);setMinimumSize(new Dimension(850,600));setLocationRelativeTo(owner);
        send(new JsonObject(),true);setVisible(true);
    }

    static JPanel glass(LayoutManager layout,int alpha){
        var panel=new JPanel(layout){
            protected void paintComponent(Graphics graphics){
                var g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(12,29,34,alpha));g.fillRoundRect(0,0,getWidth(),getHeight(),16,16);
                g.setColor(new Color(167,221,202,65));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);g.dispose();
            }
        };panel.setOpaque(false);panel.setBorder(Look.pad(13,14));return panel;
    }

    JPanel slider(String label,int min,int max,int value,java.util.function.IntConsumer change){
        var row=Look.clear(new BorderLayout(8,0));row.add(Look.label(label,12,Look.MUTED,true),BorderLayout.WEST);
        var input=new JSlider(min,max,value);input.setOpaque(false);input.getAccessibleContext().setAccessibleName(label);
        var reading=Look.label(Integer.toString(value),12,Look.PAPER,false);reading.setPreferredSize(new Dimension(30,20));
        input.setUI(new BasicSliderUI(input){
            public void paintTrack(Graphics gr){var g=(Graphics2D)gr.create();int y=trackRect.y+trackRect.height/2-2;
                g.setColor(Look.EDGE);g.fillRoundRect(trackRect.x,y,trackRect.width,4,4,4);g.setColor(Look.MINT);
                g.fillRoundRect(trackRect.x,y,thumbRect.x+thumbRect.width/2-trackRect.x,4,4,4);g.dispose();}
            public void paintThumb(Graphics gr){var g=(Graphics2D)gr.create();g.setColor(slider.hasFocus()?Look.GOLD:Look.MINT);
                g.fillOval(thumbRect.x+3,thumbRect.y+4,thumbRect.width-6,thumbRect.height-8);g.dispose();}
        });
        input.addChangeListener(e->{reading.setText(Integer.toString(input.getValue()));change.accept(input.getValue());});row.add(input);row.add(reading,BorderLayout.EAST);return row;
    }

    void send(JsonObject body,boolean replaceDraft){
        if(busy)return;busy=true;note.setText("Синхронизация с аккаунтом…");
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{
                if(steve==null)try(var jar=new java.util.zip.ZipFile(Frontier.mc().resolve("versions/1.21.1/1.21.1.jar").toFile())){
                    try(var in=jar.getInputStream(jar.getEntry("assets/minecraft/textures/entity/player/wide/steve.png"))){steve=ImageIO.read(in);}
                }catch(Exception ignored){}
                boolean test=AccountPanel.test();var result=AccountPanel.api(test,"/desktop/wardrobe",body,AccountPanel.token(test));
                if(result==null)throw new IOException("Войдите в аккаунт WoidWatch.");return result;
            }
            protected void done(){busy=false;try{
                state=get();if(replaceDraft)restore();draw();
                note.setText(body.size()==0?"Выберите вещь, чтобы примерить её.":replaceDraft?"Образ синхронизирован.":"Предмет добавлен в инвентарь.");
                if(replaceDraft&&body.size()>0)refresh.run();
            }catch(Exception ex){note.setText(ex.getCause()==null?ex.getMessage():ex.getCause().getMessage());draw();}}
        }.execute();
    }

    void restore(){fitting.clear();for(String slot:java.util.List.of("title","accessory"))fitting.put(slot,state.get(slot).getAsString());skin=state.get("skin").getAsString();}
    boolean owned(String id){return id.isEmpty()||state.getAsJsonArray("owned").contains(new JsonPrimitive(id));}
    String selected(String slot){return fitting.getOrDefault(slot,"");}
    String name(String id){
        for(var sample:SAMPLES)if(sample.id().equals(id))return sample.name();
        var item=state.getAsJsonObject("catalog").get(id);return item==null?"":item.getAsJsonArray().get(1).getAsString();
    }
    void preview(){
        avatar.skin=steve;
        if(!skin.isEmpty())try{avatar.skin=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(skin)));}catch(IOException ignored){}
        avatar.hat=artwork.get(selected("hat"));avatar.cape=artwork.get(selected("cape"));avatar.pin=selected("accessory");avatar.title=name(selected("title"));avatar.repaint();
    }
    void draw(){
        tabs.removeAll();rows.removeAll();
        var sections=new LinkedHashMap<String,String>();
        if(!store)sections.put("skin","Скин");sections.put("hat","Шапки");sections.put("cape","Плащи");
        sections.put("accessory","Броши");sections.put("title","Титулы");if(!store)sections.put("sets","Комплекты");
        sections.forEach((id,label)->{
            var tab=Look.button(label,category.equals(id));tab.setFont(Look.font(11,true));tab.setBorder(Look.pad(6,7));tabs.add(tab);
            tab.addActionListener(e->{category=id;draw();});
        });
        if(state!=null){
            preview();
            switch(category){
                case "skin" -> skinRows();
                case "sets" -> {for(String i:java.util.List.of("1","2")){
                    addAction("Записать сохранённый образ в комплект "+i,()->{if(savable()&&!dirty())send(request("save",i),true);else if(dirty())note.setText("Сначала сохраните изменения образа.");});
                    if(state.getAsJsonArray("sets").contains(new JsonPrimitive(i)))addAction("Надеть комплект "+i,()->send(request("load",i),true));
                }}
                default -> {
                    addAction("Снять предмет",()->select(""));
                    for(var item:SAMPLES)if(item.slot().equals(category))tile(item.id(),true);
                    for(var entry:state.getAsJsonObject("catalog").entrySet())if(entry.getValue().getAsJsonArray().get(0).getAsString().equals(category))tile(entry.getKey(),false);
                }
            }
        }
        tabs.revalidate();rows.revalidate();tabs.repaint();rows.repaint();
    }

    void tile(String id,boolean sample){
        var row=glass(new BorderLayout(8,0),160);rows.add(row);
        var item=new JCheckBox(name(id),selected(category).equals(id));item.setOpaque(false);item.setForeground(Look.PAPER);item.setFont(Look.font(13,true));row.add(item);
        item.addActionListener(e->select(item.isSelected()?id:""));
        if(sample){
            var icon=artwork.get(id);int scale=Math.max(icon.getWidth(),icon.getHeight());
            row.add(new JLabel(new ImageIcon(icon.getScaledInstance(Math.max(1,40*icon.getWidth()/scale),Math.max(1,40*icon.getHeight()/scale),Image.SCALE_FAST))),BorderLayout.WEST);
        }
        if(store&&!sample&&!owned(id)){
            var get=Look.button("Получить",false);row.add(get,BorderLayout.EAST);get.addActionListener(e->send(request("claim",id),false));
        }else row.add(Look.label(sample?"Образец":owned(id)?"В наличии":"В магазине",11,Look.MUTED,false),BorderLayout.EAST);
    }
    void select(String id){if(busy)return;fitting.put(category,id);draw();
        if(!id.isEmpty()&&!owned(id))note.setText("Примерка · "+name(id)+" пока недоступен для сохранения.");
        else note.setText("Образ изменён. Сохраните его, чтобы применить.");}
    void skinRows(){
        addAction("Загрузить скин · PNG 64 × 64",()->{
            if(busy)return;
            var picker=new JFileChooser();picker.setDialogTitle("Выбор скина Minecraft");
            picker.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Текстуры PNG","png"));
            if(picker.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            try{var image=Avatar.read(picker.getSelectedFile().toPath());if(image==null||image.getHeight()!=64)throw new IOException("Выберите PNG Minecraft 64×64.");
                var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);skin=Base64.getEncoder().encodeToString(out.toByteArray());
                preview();note.setText("Скин в примерочной. Нажмите «Сохранить образ».");}
            catch(Exception e){note.setText(e.getMessage());}
        });
        addAction("Выбрать Стива",()->{if(!busy){skin="";preview();note.setText("Стив выбран для примерки.");}});
    }
    void addAction(String text,Runnable action){var control=Look.button(text,false);rows.add(control);control.addActionListener(e->action.run());}
    static JsonObject request(String op,String id){var body=new JsonObject();body.addProperty("op",op);body.addProperty("item",id);return body;}
    boolean savable(){
        var missing=fitting.values().stream().filter(id->!owned(id)).map(this::name).toList();
        if(missing.isEmpty())return true;
        note.setText("Нет в инвентаре · "+String.join(", ",missing));return false;
    }
    boolean dirty(){return !skin.equals(state.get("skin").getAsString())||java.util.List.of("title","accessory","hat","cape").stream().anyMatch(slot->!selected(slot).equals(state.has(slot)?state.get(slot).getAsString():""));}
    void save(){if(state==null||busy||!savable())return;
        var body=request("apply","");body.addProperty("skin",skin);
        for(String slot:java.util.List.of("title","accessory"))body.addProperty(slot,selected(slot));
        send(body,true);
    }
}
