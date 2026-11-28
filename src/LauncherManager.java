import java.awt.Desktop;
import java.util.function.Consumer;
import javax.swing.*;

final class LauncherManager {
    static void open(JFrame owner,JButton settings,Runnable importPack,Consumer<String> launch){
        var dialog=new JDialog(owner,"Менеджер WoidWatch",true);
        var panel=Look.dialog(dialog,"Менеджер","Настройки, файлы сборки и сервер сообщества.");
        var status=Look.label(" ",13,Look.GOLD,false);
        var game=Look.button("Подключиться к серверу сообщества",true);
        var configure=Look.button("Настройки лаунчера",false);
        var folder=Look.button("Открыть папку сборки",false);
        var attach=Look.button("Подключить другую папку сборки…",false);
        var server=Look.button("Настроить адрес сервера",false);
        panel.add(game);panel.add(configure);panel.add(folder);panel.add(attach);panel.add(server);panel.add(status);
        configure.addActionListener(e->{dialog.dispose();settings.doClick();});
        folder.addActionListener(e->{try{Desktop.getDesktop().open(Library.location().toFile());}catch(Exception failure){status.setText("Не удалось открыть папку сборки.");}});
        attach.addActionListener(e->{dialog.dispose();importPack.run();});
        server.addActionListener(e->Community.admin(owner,server,status));
        game.addActionListener(e->{
            game.setEnabled(false);status.setText("Проверка адреса…");
            new SwingWorker<String,Void>(){
                protected String doInBackground()throws Exception{return Community.refresh();}
                protected void done(){game.setEnabled(true);
                    try{String address=get();if(address.isEmpty()){status.setText("Адрес сервера ещё не задан.");return;}
                        dialog.dispose();launch.accept(address);
                    }catch(Exception failure){status.setText("Не удалось получить адрес сервера.");}
                }
            }.execute();
        });
        dialog.pack();dialog.setLocationRelativeTo(owner);dialog.setVisible(true);
    }
}
