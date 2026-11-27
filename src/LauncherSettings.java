import javax.swing.*;
import java.awt.*;
import java.nio.file.*;
import com.google.gson.*;

final class LauncherSettings {
    static Path file(){return LauncherUpdates.home().resolve("appearance.json");}
    static JsonObject read()throws Exception{return Files.exists(file())?Pack.read(file()):new JsonObject();}
    static int memory(JsonObject j){return j.has("memory")?Math.clamp(j.get("memory").getAsInt(),2,16):6;}
    static boolean motion(JsonObject j){return !j.has("motion")||j.get("motion").getAsBoolean();}
    static void open(JFrame owner,Avatar avatar,String player,Runnable merge)throws Exception{
        var values=read();var d=new JDialog(owner,"Настройки WoidWatch",true);
        var p=new JPanel(new GridLayout(0,1,0,8));p.setBackground(Look.INK);p.setBorder(Look.pad(20,24));
        p.add(Look.label("Настройки",25,Look.PAPER,true));p.add(Look.label("Память для Minecraft, ГБ",14,Look.MUTED,false));
        JSpinner ram=new JSpinner(new SpinnerNumberModel(memory(values),2,16,1));p.add(ram);
        var animate=new JCheckBox("Персонаж следит за курсором",motion(values));p.add(animate);
        var choose=Look.button("Выбрать PNG скина для лаунчера",false);p.add(choose);
        choose.addActionListener(e->{var chooser=new JFileChooser();chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Скин Minecraft PNG","png"));if(chooser.showOpenDialog(d)!=JFileChooser.APPROVE_OPTION)return;
            try{var skin=Avatar.read(chooser.getSelectedFile().toPath());if(skin==null)throw new Exception("Нужен PNG 64×64 или 64×32 до 1 МБ.");
                Path to=Avatar.custom(player);Files.createDirectories(to.getParent());Path tmp=Files.createTempFile(to.getParent(),"skin-",".png");try{javax.imageio.ImageIO.write(skin,"png",tmp.toFile());Pack.move(tmp,to);}finally{Files.deleteIfExists(tmp);}
                avatar.load(Library.location(),player);
            }catch(Exception ex){JOptionPane.showMessageDialog(d,ex.getMessage());}});
        var imp=Look.button("Подключить папку сборки…",false);imp.addActionListener(e->merge.run());p.add(imp);
        var save=Look.button("Сохранить",true);p.add(save);save.addActionListener(e->{try{values.addProperty("memory",(Integer)ram.getValue());values.addProperty("motion",animate.isSelected());Pack.write(file(),values.toString());avatar.follow=animate.isSelected();d.dispose();}catch(Exception ex){JOptionPane.showMessageDialog(d,ex.getMessage());}});
        d.add(p);d.pack();d.setLocationRelativeTo(owner);d.setVisible(true);
    }
}
