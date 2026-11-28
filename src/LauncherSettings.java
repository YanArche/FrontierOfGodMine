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
        var p=Look.dialog(d,"Настройки","Память Minecraft, анимация и папка сборки.");
        var ram=new JSlider(2,16,memory(values));ram.setOpaque(false);
        ram.setMajorTickSpacing(2);ram.setMinorTickSpacing(1);ram.setPaintTicks(true);ram.setSnapToTicks(true);
        ram.getAccessibleContext().setAccessibleName("Память для Minecraft");
        var amount=Look.label("Память для Minecraft · "+ram.getValue()+" ГБ",14,Look.PAPER,true);
        ram.addChangeListener(e->amount.setText("Память для Minecraft · "+ram.getValue()+" ГБ"));
        p.add(amount);p.add(ram);
        var animate=Look.toggle("Персонаж следит за курсором",motion(values));p.add(animate);
        var imp=Look.button("Подключить папку сборки…",false);imp.addActionListener(e->merge.run());p.add(imp);
        var save=Look.button("Сохранить",true);p.add(save);save.addActionListener(e->{try{values.addProperty("memory",ram.getValue());values.addProperty("motion",animate.isSelected());Pack.write(file(),values.toString());avatar.follow=animate.isSelected();d.dispose();}catch(Exception ex){JOptionPane.showMessageDialog(d,ex.getMessage());}});
        d.getRootPane().setDefaultButton(save);d.pack();d.setLocationRelativeTo(owner);d.setVisible(true);
    }
}
