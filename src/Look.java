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
                var model=getModel();
                Color bg=!isEnabled()?new Color(35,46,50):primary?MINT:PANEL;
                if(isEnabled()&&model.isPressed()&&model.isArmed())bg=bg.darker();
                else if(isEnabled()&&model.isRollover())bg=primary?new Color(143,255,225):new Color(38,61,67);
                a.setPaint(new GradientPaint(0,0,bg,0,Math.max(1,getHeight()),bg.darker()));
                a.fillRoundRect(1,1,getWidth()-2,getHeight()-2,14,14);
                a.setColor(hasFocus()?GOLD:primary&&isEnabled()?MINT:EDGE);
                a.drawRoundRect(1,1,getWidth()-3,getHeight()-3,14,14);
                if(hasFocus())a.drawRoundRect(3,3,getWidth()-7,getHeight()-7,11,11);
                a.dispose();super.paintComponent(g);}
        };b.setOpaque(false);b.setContentAreaFilled(false);b.setBorder(pad(11,16));b.setFocusPainted(false);
        b.setForeground(primary?INK:PAPER);b.setFont(font(14,true));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));return b;
    }
    static JCheckBox toggle(String caption,boolean selected){
        var control=new JCheckBox(caption,selected);
        control.setOpaque(false);control.setForeground(PAPER);control.setFont(font(14,true));
        control.setIconTextGap(12);control.setBorder(pad(8,2));
        Icon knob=new Icon(){
            public int getIconWidth(){return 36;}
            public int getIconHeight(){return 22;}
            public void paintIcon(Component c,Graphics graphics,int x,int y){
                var g=(Graphics2D)graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                boolean on=control.isSelected();g.setColor(on&&control.isEnabled()?MINT:EDGE);
                g.fillRoundRect(x,y,36,22,22,22);g.setColor(on?INK:PAPER);g.fillOval(x+(on?17:4),y+4,14,14);
                if(control.hasFocus()){g.setColor(GOLD);g.drawRoundRect(x,y,35,21,22,22);}
                g.dispose();
            }
        };
        control.setIcon(knob);control.setSelectedIcon(knob);return control;
    }
    static JPanel field(String caption,JTextField input){
        var row=clear(new BorderLayout(0,6));var label=Look.label(caption,12,MUTED,true);
        label.setLabelFor(input);row.add(label,BorderLayout.NORTH);row.add(input);
        input.setOpaque(true);input.setBackground(new Color(8,18,25));input.setForeground(PAPER);input.setCaretColor(MINT);input.setFont(font(15,false));
        input.setBorder(new javax.swing.border.AbstractBorder(){
            public Insets getBorderInsets(Component c,Insets inset){inset.set(10,12,10,12);return inset;}
            public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){
                var g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(input.hasFocus()?MINT:EDGE);g.drawRoundRect(x,y,w-1,h-1,12,12);g.dispose();
            }
        });
        input.addFocusListener(new java.awt.event.FocusAdapter(){
            public void focusGained(java.awt.event.FocusEvent e){input.repaint();}
            public void focusLost(java.awt.event.FocusEvent e){input.repaint();}
        });
        return row;
    }
    static JPanel dialog(JDialog dialog,String title,String description){
        var scene=new Scene();scene.setBorder(pad(22,22));dialog.setContentPane(scene);
        var panel=Wardrobe.glass(new BorderLayout(0,14),235);scene.add(panel);
        var header=clear(new GridLayout(0,1,0,5));header.add(label(title,28,PAPER,true));header.add(label(description,13,MUTED,false));
        panel.add(header,BorderLayout.NORTH);var body=new JPanel(){
            public Component add(Component component){
                var constraints=new GridBagConstraints();
                constraints.gridx=0;constraints.gridy=getComponentCount();constraints.weightx=1;
                constraints.fill=GridBagConstraints.HORIZONTAL;constraints.insets=new Insets(0,0,9,0);
                add(component,constraints);return component;
            }
        };
        body.setLayout(new GridBagLayout());body.setOpaque(false);panel.add(body);return body;
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
