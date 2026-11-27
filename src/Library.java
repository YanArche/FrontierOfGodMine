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
            
            JFrame frame=new JFrame("WoidWatch Launcher "+LauncherUpdates.VERSION);frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JPanel panel=new JPanel();panel.setBackground(new Color(17,17,24));panel.setBorder(BorderFactory.createEmptyBorder(24,30,24,30));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
            JLabel heading=new JLabel("WOIDWATCH   /   Библиотека");heading.setForeground(new Color(0,230,214));panel.add(heading);
            JLabel name=new JLabel("Frontier of God");name.setFont(new Font("Serif",Font.BOLD,36));name.setForeground(Color.WHITE);panel.add(name);
            JLabel info=new JLabel(),path=new JLabel(),status=new JLabel(" ");for(var label:new JLabel[]{info,path,status}){label.setForeground(Color.LIGHT_GRAY);panel.add(label);}
            Runnable refresh=()->{try{Path dir=location();info.setText(LocalPlayer.display(dir)+"   ·   Сборка "+version(dir)+"   ·   Minecraft 1.21.1   ·   NeoForge 21.1.267");path.setText(dir.toString());}catch(Exception e){status.setText(e.getMessage());}};
            refresh.run();Path rollback=LauncherUpdates.home().resolve("rollback.txt");if(Files.isRegularFile(rollback)){status.setText(Files.readString(rollback));Files.deleteIfExists(rollback);}
            JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.setOpaque(false);actions.setAlignmentX(Component.LEFT_ALIGNMENT);panel.add(actions);
            JButton play=new JButton("Играть"),imp=new JButton("Импортировать сборку");actions.add(play);actions.add(imp);
            JButton profile=new JButton("Мой профиль");actions.add(profile);
            profile.addActionListener(e->{try{LocalPlayer.edit(frame,location());refresh.run();}catch(Exception ex){status.setText(ex.getMessage());}});
            imp.addActionListener(e->{JFileChooser picker=new JFileChooser();picker.setDialogTitle("Папка Frontier of God с mods, saves и options.txt");picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if(picker.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)try{link(picker.getSelectedFile().toPath());refresh.run();status.setText("Подключена существующая папка с мирами и настройками.");}catch(Exception ex){JOptionPane.showMessageDialog(frame,ex.getMessage());}});
            CardLayout cards=new CardLayout();JPanel pages=new JPanel(cards);pages.add(panel,"library");
            JPanel loading=new JPanel();loading.setBackground(panel.getBackground());loading.setLayout(new BoxLayout(loading,BoxLayout.Y_AXIS));
            loading.setBorder(BorderFactory.createEmptyBorder(28,32,28,32));
            JLabel gameTitle=new JLabel("Frontier of God");gameTitle.setForeground(heading.getForeground());gameTitle.setFont(name.getFont());loading.add(gameTitle);
            JLabel phase=new JLabel("Подготовка");phase.setForeground(Color.WHITE);loading.add(phase);
            JProgressBar progress=new JProgressBar();progress.setForeground(heading.getForeground());loading.add(progress);
            JLabel detail=new JLabel(" ");detail.setForeground(Color.LIGHT_GRAY);loading.add(detail);pages.add(loading,"loading");
            play.addActionListener(event->{
                cards.show(pages,"loading");progress.setIndeterminate(true);detail.setText(" ");
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
                    String message=result;SwingUtilities.invokeLater(()->{cards.show(pages,"library");refresh.run();status.setText(message);frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);});
                }).start();
            });
            JLabel updates=new JLabel("Проверка обновлений лаунчера…");updates.setForeground(new Color(110,196,187));panel.add(updates);
            new Timer(1500,e->{try{Path f=LauncherUpdates.home().resolve("update-status.txt");if(Files.exists(f))updates.setText(Files.readString(f));}catch(IOException ignored){}}).start();
            frame.setContentPane(pages);frame.setSize(880,320);frame.setLocationRelativeTo(null);frame.setVisible(true);if(ready!=null)Files.createFile(ready);
        }catch(Exception ex){ex.printStackTrace();System.exit(1);}
    }
}
