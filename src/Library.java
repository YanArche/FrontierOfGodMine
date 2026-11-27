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
            boolean local=Boolean.getBoolean("woidwatch.local");
            JFrame frame=new JFrame("WoidWatch Launcher "+LauncherUpdates.VERSION);frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            JPanel panel=new JPanel();panel.setBackground(new Color(17,17,24));panel.setBorder(BorderFactory.createEmptyBorder(24,30,24,30));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
            JLabel heading=new JLabel("WOIDWATCH   /   Библиотека");heading.setForeground(new Color(0,230,214));panel.add(heading);
            JLabel name=new JLabel("Frontier of God");name.setFont(new Font("Serif",Font.BOLD,36));name.setForeground(Color.WHITE);panel.add(name);
            JLabel info=new JLabel(),path=new JLabel(),status=new JLabel(" ");for(var label:new JLabel[]{info,path,status}){label.setForeground(Color.LIGHT_GRAY);panel.add(label);}
            Runnable refresh=()->{try{Path dir=location();info.setText("Сборка "+version(dir)+"   ·   Minecraft 1.21.1   ·   NeoForge 21.1.267");path.setText(dir.toString());}catch(Exception e){status.setText(e.getMessage());}};
            refresh.run();Path rollback=LauncherUpdates.home().resolve("rollback.txt");if(Files.isRegularFile(rollback)){status.setText(Files.readString(rollback));Files.deleteIfExists(rollback);}
            JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.setOpaque(false);actions.setAlignmentX(Component.LEFT_ALIGNMENT);panel.add(actions);
            JButton play=new JButton(local?"Играть локально":"Играть"),imp=new JButton("Импортировать сборку");actions.add(play);actions.add(imp);
            JButton account=new JButton("Мой аккаунт");if(!local)actions.add(account);account.addActionListener(e->WoidAccount.login(frame,account,status));
            JButton admin=new JButton("Администрирование");if(!local)actions.add(admin);admin.addActionListener(e->Community.admin(frame,admin,status));
            if(!local)new Thread(()->{try{String a=Community.refresh();Path selected=location();
                boolean playing=ProcessHandle.allProcesses().anyMatch(p->p.info().arguments().map(args->java.util.Arrays.asList(args).contains("--gameDir")).orElse(false));
                if(!playing&&Files.isDirectory(selected))ServerListFile.sync(selected,a);SwingUtilities.invokeLater(()->status.setText(a.isEmpty()?"Сервер сообщества пока не открыт":"Сервер сообщества: "+a));}catch(Exception ignored){}}).start();
            imp.addActionListener(e->{JFileChooser picker=new JFileChooser();picker.setDialogTitle("Папка Frontier of God с mods, saves и options.txt");picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if(picker.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION)try{link(picker.getSelectedFile().toPath());refresh.run();status.setText("Подключена существующая папка с мирами и настройками.");}catch(Exception ex){JOptionPane.showMessageDialog(frame,ex.getMessage());}});
            CardLayout cards=new CardLayout();JPanel pages=new JPanel(cards);pages.add(panel,"library");
            JPanel loading=new JPanel();loading.setBackground(panel.getBackground());loading.setLayout(new BoxLayout(loading,BoxLayout.Y_AXIS));
            loading.setBorder(BorderFactory.createEmptyBorder(28,32,28,32));
            JLabel gameTitle=new JLabel("Frontier of God");gameTitle.setForeground(heading.getForeground());gameTitle.setFont(name.getFont());loading.add(gameTitle);
            JLabel phase=new JLabel("Подготовка");phase.setForeground(Color.WHITE);loading.add(phase);
            JProgressBar progress=new JProgressBar();progress.setForeground(heading.getForeground());loading.add(progress);
            JLabel detail=new JLabel(" ");detail.setForeground(Color.LIGHT_GRAY);loading.add(detail);pages.add(loading,"loading");
            JButton minecraft=new JButton("Войти в Minecraft");if(!local)actions.add(minecraft);
            java.util.function.Consumer<Boolean> start=launch->{
                if(!local&&MinecraftAccount.clientId().isEmpty()){
                    status.setText("Вход Microsoft ожидает регистрации приложения WoidWatch.");
                    return;
                }
                cards.show(pages,"loading");progress.setIndeterminate(true);detail.setText(" ");
                phase.setText(local?"Локальный профиль":"Вход в Minecraft");frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                new Thread(()->{
                    String result;
                    try{
                        if(launch){
                            Path dir=location();if(Files.exists(settings()))imported(dir);
                            Frontier.run(dir,false,false,local,msg->SwingUtilities.invokeLater(()->{
                                if(msg.startsWith("Ресурсы Minecraft: ")){
                                    String[] counts=msg.substring("Ресурсы Minecraft: ".length()).split(" / ");
                                    progress.setIndeterminate(false);progress.setMaximum(Integer.parseInt(counts[1]));progress.setValue(Integer.parseInt(counts[0]));
                                    phase.setText("Проверка ресурсов Minecraft");detail.setText(counts[0]+" из "+counts[1]);
                                }else if(msg.startsWith("Обновление: ")){phase.setText("Обновление сборки");detail.setText(msg.substring("Обновление: ".length()));}
                                else{phase.setText(msg);detail.setText(" ");progress.setIndeterminate(!msg.equals("Интерфейс Minecraft загружен"));}
                            }));
                            result="Игра закрыта.";
                        }else{var player=MinecraftAccount.signIn("player");result="Minecraft: "+player.name();}
                    }catch(Exception ex){result=ex.getMessage()==null?"Не удалось завершить запуск":ex.getMessage();}
                    String message=result;SwingUtilities.invokeLater(()->{cards.show(pages,"library");refresh.run();status.setText(message);frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);});
                }).start();
            };
            play.addActionListener(e->start.accept(true));minecraft.addActionListener(e->start.accept(false));
            JLabel updates=new JLabel("Проверка обновлений лаунчера…");updates.setForeground(new Color(110,196,187));panel.add(updates);
            if(local)updates.setText("Одиночная игра · установленная сборка");else new Timer(1500,e->{try{Path f=LauncherUpdates.home().resolve("update-status.txt");if(Files.exists(f))updates.setText(Files.readString(f));}catch(IOException ignored){}}).start();
            frame.setContentPane(pages);frame.setSize(880,320);frame.setLocationRelativeTo(null);frame.setVisible(true);if(ready!=null)Files.createFile(ready);
        }catch(Exception ex){ex.printStackTrace();System.exit(1);}
    }
}
