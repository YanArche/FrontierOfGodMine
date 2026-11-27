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
            Runnable refresh=()->{try{Path dir=location();info.setText("Сборка "+version(dir)+"   ·   Minecraft 1.21.1   ·   NeoForge 21.1.267");path.setText(dir.toString());}catch(Exception e){status.setText(e.getMessage());}};
            refresh.run();Path rollback=LauncherUpdates.home().resolve("rollback.txt");if(Files.isRegularFile(rollback)){status.setText(Files.readString(rollback));Files.deleteIfExists(rollback);}
            JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.setOpaque(false);actions.setAlignmentX(Component.LEFT_ALIGNMENT);panel.add(actions);
            JButton play=new JButton("Играть"),imp=new JButton("Импортировать сборку");actions.add(play);actions.add(imp);
            imp.addActionListener(e->{JFileChooser picker=new JFileChooser();picker.setDialogTitle("Папка Frontier of God с mods, saves и options.txt");picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if(picker.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)try{link(picker.getSelectedFile().toPath());refresh.run();status.setText("Подключена существующая папка с мирами и настройками.");}catch(Exception ex){JOptionPane.showMessageDialog(frame,ex.getMessage());}});
            play.addActionListener(e->{play.setEnabled(false);imp.setEnabled(false);frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                new Thread(()->{try{Path dir=location();if(Files.exists(settings()))imported(dir);Frontier.run(dir,false,false,false,msg->SwingUtilities.invokeLater(()->status.setText(msg)));}
                    catch(Exception ex){SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(frame,ex.getMessage()));}
                    finally{SwingUtilities.invokeLater(()->{play.setEnabled(true);imp.setEnabled(true);refresh.run();frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);});}}).start();});
            JLabel updates=new JLabel("Проверка обновлений лаунчера…");updates.setForeground(new Color(110,196,187));panel.add(updates);
            new Timer(1500,e->{try{Path f=LauncherUpdates.home().resolve("update-status.txt");if(Files.exists(f))updates.setText(Files.readString(f));}catch(IOException ignored){}}).start();
            frame.setContentPane(panel);frame.setSize(740,290);frame.setLocationRelativeTo(null);frame.setVisible(true);Files.createFile(ready);
        }catch(Exception ex){ex.printStackTrace();System.exit(1);}
    }
}
