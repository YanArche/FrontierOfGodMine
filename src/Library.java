import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import java.io.IOException;
import com.google.gson.*;

final class Library {
    static final String ID="frontier-of-god";
    static Path settings(){return LauncherUpdates.home().resolve("library.json");}
    static Path imported(Path dir)throws IOException{
        Path path=dir.toRealPath();
        if(!Files.isDirectory(path.resolve("mods")))throw new IOException("Выберите папку сборки, содержащую mods.");
        try(var files=Files.newDirectoryStream(path.resolve("mods"),"frontier-campaign-data-*.jar")){
            if(!files.iterator().hasNext())throw new IOException("В этой папке не найдена кампания Frontier of God.");
        }
        return path;
    }
    static void link(Path dir)throws Exception{
        Path path=imported(dir);JsonObject library=Files.exists(settings())?Pack.read(settings()):new JsonObject();
        library.addProperty(ID,path.toString());Pack.write(settings(),Pack.JSON.toJson(library));
    }
    static Path location()throws Exception{
        if(Files.exists(settings())){var value=Pack.read(settings()).get(ID);if(value!=null)return Path.of(value.getAsString());}
        return Frontier.game();
    }
    static String version(Path dir)throws Exception{
        Path index=dir.resolve(".frontier/index.json");if(!Files.exists(index))return Files.exists(dir.resolve("mods"))?"импортирована":"доступна для установки";
        var v=Pack.read(index).get("packVersion");return v==null?"1.0.0":v.getAsString();
    }
    static void open(Path ready){
        try{
            
            Look.defaults();
            JFrame frame=new JFrame("WoidWatch Launcher "+LauncherUpdates.VERSION);frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setIconImages(java.util.List.of(16,24,32,48,64,128,256).stream().map(n->Look.EYE.getScaledInstance(n,n,Image.SCALE_SMOOTH)).toList());
            if(Taskbar.isTaskbarSupported()&&Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE))Taskbar.getTaskbar().setIconImage(Look.EYE);
            JPanel root=new JPanel(new BorderLayout(0,0));root.setBackground(Look.INK);
            JPanel side=new JPanel(new BorderLayout(0,12));side.setBackground(Look.INK);side.setPreferredSize(new Dimension(218,0));side.setBorder(Look.pad(18,16));root.add(side,BorderLayout.WEST);
            JPanel top=Look.clear(new BorderLayout(0,5));side.add(top,BorderLayout.NORTH);
            JLabel brand=Look.label("  WoidWatch",21,Look.PAPER,true);brand.setIcon(new ImageIcon(Look.EYE.getScaledInstance(36,36,Image.SCALE_SMOOTH)));top.add(brand,BorderLayout.NORTH);
            Avatar avatar=new Avatar();avatar.follow=LauncherSettings.motion(LauncherSettings.read());top.add(avatar,BorderLayout.CENTER);
            JButton account=Look.button("Аккаунт",false);top.add(account,BorderLayout.SOUTH);
            JPanel nav=Look.clear(new BorderLayout(0,9));side.add(nav,BorderLayout.CENTER);
            nav.add(Look.label("МОИ СБОРКИ",11,Look.MUTED,true),BorderLayout.NORTH);JPanel items=Look.clear(new FlowLayout(FlowLayout.LEFT,0,0));nav.add(items);
            JButton pack=Look.button("Frontier of God",false);pack.setIcon(new ImageIcon(Look.ART.getScaledInstance(38,29,Image.SCALE_SMOOTH)));pack.setPreferredSize(new Dimension(186,50));pack.setFont(Look.font(13,true));pack.setBorder(Look.pad(7,6));pack.setForeground(Look.GOLD);items.add(pack);
            JButton settings=Look.button("Настройки лаунчера",false);side.add(settings,BorderLayout.SOUTH);
            JPanel right=new JPanel(new BorderLayout(0,12));right.setBackground(new Color(19,29,35));right.setBorder(Look.pad(17,19));root.add(right);
            JPanel bar=Look.clear(new BorderLayout());bar.add(Look.label("Библиотека",19,Look.PAPER,true),BorderLayout.WEST);JLabel updates=Look.label("WoidWatch   /   "+LauncherUpdates.VERSION,12,Look.MUTED,false);bar.add(updates,BorderLayout.EAST);right.add(bar,BorderLayout.NORTH);
            CardLayout cards=new CardLayout();JPanel pages=new JPanel(cards);pages.setOpaque(false);right.add(pages);
            Look.Scene scene=new Look.Scene();pages.add(scene,"library");scene.setBorder(Look.pad(28,34));
            JPanel intro=Look.clear(new GridLayout(0,1,0,0));intro.add(Look.label("MINECRAFT  JAVA EDITION",12,Look.GOLD,true));
            JLabel title=Look.label("Frontier of God",54,Look.PAPER,false);title.setFont(new Font("Georgia",Font.BOLD,54));intro.add(title);
            intro.add(Look.label("Исследования, магия и механизмы Create",16,Look.PAPER,false));
            JButton play=Look.button("Играть",true);play.setPreferredSize(new Dimension(150,42));play.setIcon(new Icon(){public int getIconWidth(){return 18;}public int getIconHeight(){return 14;}public void paintIcon(Component c,Graphics g,int x,int y){g.setColor(Look.INK);g.fillPolygon(new int[]{x,x,x+10},new int[]{y,y+14,y+7},3);}});
            JPanel caption=Look.clear(new BorderLayout(0,12));caption.add(intro);JPanel row=Look.clear(new FlowLayout(FlowLayout.LEFT,0,0));row.add(play);caption.add(row,BorderLayout.SOUTH);caption.setBorder(Look.pad(14,18));JPanel plate=new JPanel(new BorderLayout()){protected void paintComponent(Graphics gr){var g=(Graphics2D)gr.create();g.setColor(new Color(14,30,33,148));g.fillRoundRect(0,0,getWidth(),getHeight(),14,14);g.setColor(new Color(214,197,144,80));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,14,14);g.dispose();}};plate.setOpaque(false);plate.add(caption);JPanel dock=Look.clear(new BorderLayout());dock.add(plate,BorderLayout.WEST);scene.add(dock,BorderLayout.SOUTH);
            JPanel footer=Look.clear(new BorderLayout(0,8));right.add(footer,BorderLayout.SOUTH);
            JPanel spec=new JPanel(new GridLayout(1,3,12,0));spec.setBackground(Look.PANEL);spec.setBorder(Look.pad(10,15));
            JLabel info=Look.label("—",15,Look.PAPER,true);
            String[] labels={"СБОРКА","MINECRAFT","ЗАГРУЗЧИК"};
            JLabel[] vals={info,Look.label("Java 1.21.1",15,Look.PAPER,true),Look.label("NeoForge 21.1.267",15,Look.PAPER,true)};
            for(int i=0;i<3;i++){JPanel group=Look.clear(new GridLayout(2,1));group.add(Look.label(labels[i],10,Look.MUTED,true));group.add(vals[i]);spec.add(group);}footer.add(spec);
            JLabel status=Look.label(" ",12,Look.MUTED,false);footer.add(status,BorderLayout.SOUTH);
            String[] player={"Player"};boolean[] playing={false};
            Runnable refresh=()->{try{Path dir=location();info.setText(version(dir));spec.setToolTipText(dir.toString());}catch(Exception ex){status.setText(ex.getMessage());}};
            Runnable user=()->new Thread(()->{try{Path dir=location();String local=LocalPlayer.display(dir);
                String nick=local.matches("[a-zA-Z0-9_]{1,16}")?local:"Player";boolean signed=false;
                try{var me=AccountPanel.current(AccountPanel.test());if(me!=null){nick=me.get("name").getAsString();signed=true;}}catch(Exception ignored){}
                String who=nick;boolean logged=signed;SwingUtilities.invokeLater(()->{player[0]=who;account.setText(logged?who+"   ·   Аккаунт":who+"   ·   Войти");avatar.load(dir,who);});
            }catch(Exception ex){SwingUtilities.invokeLater(()->status.setText(ex.getMessage()));}},"WoidWatch profile").start();
            refresh.run();user.run();
            Path rollback=LauncherUpdates.home().resolve("rollback.txt");if(Files.isRegularFile(rollback)){status.setText(Files.readString(rollback));Files.deleteIfExists(rollback);}
            account.addActionListener(e->{try{AccountPanel.open(frame,status);user.run();}catch(Exception ex){status.setText(ex.getMessage());}});
            Runnable imp=()->{JFileChooser picker=new JFileChooser();picker.setDialogTitle("Папка Frontier of God");picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if(picker.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)try{link(picker.getSelectedFile().toPath());refresh.run();user.run();status.setText("Папка сборки подключена.");}catch(Exception ex){JOptionPane.showMessageDialog(frame,ex.getMessage());}};
            settings.addActionListener(e->{try{LauncherSettings.open(frame,avatar,player[0],imp);}catch(Exception ex){status.setText(ex.getMessage());}});
            pack.addActionListener(e->{if(!playing[0])cards.show(pages,"library");});
            Look.Scene loading=new Look.Scene();pages.add(loading,"loading");loading.setBorder(Look.pad(32,32));
            JPanel progressBox=new JPanel(new GridLayout(0,1,0,8));progressBox.setOpaque(false);progressBox.setBorder(Look.pad(12,4));
            JLabel phase=Look.label("Подготовка",24,Look.PAPER,true),detail=Look.label(" ",14,Look.PAPER,false);JProgressBar progress=new JProgressBar();progress.setForeground(Look.MINT);progress.setBackground(Look.PANEL);
            progressBox.add(phase);progressBox.add(progress);progressBox.add(detail);loading.add(progressBox,BorderLayout.SOUTH);
            play.addActionListener(event->{
                playing[0]=true;settings.setEnabled(false);account.setEnabled(false);cards.show(pages,"loading");progress.setIndeterminate(true);detail.setText(" ");
                phase.setText("Локальный профиль");frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                new Thread(()->{
                    String result;
                    try{

                            Path dir=location();if(Files.exists(settings()))imported(dir);
                            Frontier.run(dir,false,false,true,msg->SwingUtilities.invokeLater(()->{
                                if(msg.startsWith("Ресурсы Minecraft: ")){
                                    String[] counts=msg.substring("Ресурсы Minecraft: ".length()).split(" / ");
                                    progress.setIndeterminate(false);progress.setMaximum(Integer.parseInt(counts[1]));progress.setValue(Integer.parseInt(counts[0]));
                                    phase.setText("Проверка ресурсов Minecraft");detail.setText(counts[0]+" из "+counts[1]);
                                }else if(msg.startsWith("Обновление: ")){phase.setText("Обновление сборки");detail.setText(msg.substring("Обновление: ".length()));}
                                else{phase.setText(msg);detail.setText(" ");progress.setIndeterminate(!msg.equals("Интерфейс Minecraft загружен"));}
                            }));
                            result="Игра закрыта.";

                    }catch(Exception ex){result=ex.getMessage()==null?"Не удалось завершить запуск":ex.getMessage();}
                    String message=result;SwingUtilities.invokeLater(()->{playing[0]=false;settings.setEnabled(true);account.setEnabled(true);cards.show(pages,"library");refresh.run();status.setText(message);frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);});
                }).start();
            });
            new Timer(3500,e->{try{Path f=LauncherUpdates.home().resolve("update-status.txt");if(Files.exists(f))updates.setToolTipText(Files.readString(f));}catch(IOException ignored){}}).start();
            frame.setContentPane(root);frame.setMinimumSize(new Dimension(810,530));frame.setSize(1110,680);frame.setLocationRelativeTo(null);frame.setVisible(true);if(ready!=null)Files.createFile(ready);
        }catch(Exception ex){ex.printStackTrace();System.exit(1);}
    }
}
