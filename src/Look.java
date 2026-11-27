import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class Look {
    static final Color INK=new Color(14,22,29), PANEL=new Color(24,37,45), EDGE=new Color(61,79,84), PAPER=new Color(243,240,225), MUTED=new Color(166,185,184), MINT=new Color(101,233,204), GOLD=new Color(233,201,142);
    static final BufferedImage ART=art("frontier.jpg"), EYE=art("eye.png");
    static BufferedImage art(String name){try(var in=Look.class.getResourceAsStream("/art/"+name)){return ImageIO.read(in);}catch(Exception e){throw new IllegalStateException(name,e);}}
    static Font font(int size,boolean bold){return new Font("Segoe UI",bold?Font.BOLD:Font.PLAIN,size);}
    static JLabel label(String s,int size,Color c,boolean bold){var l=new JLabel(s);l.setFont(font(size,bold));l.setForeground(c);return l;}
    static JPanel clear(LayoutManager layout){var p=new JPanel(layout);p.setOpaque(false);return p;}
    static EmptyBorder pad(int y,int x){return new EmptyBorder(y,x,y,x);}
    static JButton button(String text,boolean primary){
        JButton b=new JButton(text){
            protected void paintComponent(Graphics g){var a=(Graphics2D)g.create();a.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg=primary?MINT:PANEL;if(getModel().isRollover())bg=bg.brighter();a.setColor(bg);a.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                a.setColor(hasFocus()?GOLD:primary?MINT:EDGE);a.drawRoundRect(0,0,getWidth()-1,getHeight()-1,10,10);a.dispose();super.paintComponent(g);}
        };b.setOpaque(false);b.setContentAreaFilled(false);b.setBorder(pad(9,14));b.setFocusPainted(false);b.setForeground(primary?INK:PAPER);b.setFont(font(14,true));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
    static void cover(Graphics2D g,BufferedImage im,int w,int h){double s=Math.max(w/(double)im.getWidth(),h/(double)im.getHeight());int iw=(int)Math.ceil(im.getWidth()*s),ih=(int)Math.ceil(im.getHeight()*s);g.drawImage(im,(w-iw)/2,(h-ih)/2,iw,ih,null);}
    static class Scene extends JPanel{
        Scene(){super(new BorderLayout());setOpaque(false);}
        protected void paintComponent(Graphics gr){Graphics2D g=(Graphics2D)gr.create();int w=getWidth(),h=getHeight();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.clip(new RoundRectangle2D.Float(0,0,w,h,20,20));
            cover(g,ART,w,h);g.setPaint(new GradientPaint(0,h*.32f,new Color(8,19,23,0),0,h,new Color(6,18,22,248)));g.fillRect(0,0,w,h);
            g.setPaint(new GradientPaint(0,0,new Color(8,18,23,105),w*.8f,0,new Color(8,18,23,0)));g.fillRect(0,0,w,h);g.dispose();}
    }
    static void defaults(){
        for(String k:new String[]{"Panel.background","OptionPane.background","FileChooser.background"})UIManager.put(k,INK);
        for(String k:new String[]{"Label.foreground","CheckBox.foreground","OptionPane.messageForeground","TextField.foreground","PasswordField.foreground","ComboBox.foreground","List.foreground","Spinner.foreground","Button.foreground"})UIManager.put(k,PAPER);
        for(String k:new String[]{"TextField.background","PasswordField.background","ComboBox.background","List.background","Button.background","CheckBox.background"})UIManager.put(k,PANEL);
        UIManager.put("TextField.caretForeground",MINT);UIManager.put("PasswordField.caretForeground",MINT);
    }
}
