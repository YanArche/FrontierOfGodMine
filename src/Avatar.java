import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import com.google.gson.*;

final class Avatar extends JComponent {
    BufferedImage skin;boolean slim,follow=true;double yaw=-.24,pitch=.07;String identity="";
    BufferedImage hat,cape; String pin="", title=""; double zoom=1, turn=0; boolean studio;
    final javax.swing.Timer motion=new javax.swing.Timer(45,e->track());
    Avatar(){setPreferredSize(new Dimension(160,145));}
    public void addNotify(){super.addNotify();motion.start();}
    public void removeNotify(){motion.stop();super.removeNotify();}
    void track(){if(!isShowing())return;double y=-.24,p=.07;
        if(follow){var pointer=MouseInfo.getPointerInfo();if(pointer!=null){Point c=getLocationOnScreen(),m=pointer.getLocation();y=Math.clamp((m.x-c.x-getWidth()/2)/200.,-.68,.68);p=Math.clamp((c.y+getHeight()*.28-m.y)/220.,-.35,.35);}}
        if(Math.abs(y-yaw)+Math.abs(p-pitch)>.006){yaw+=(y-yaw)*.25;pitch+=(p-pitch)*.25;repaint();}}
    static Path custom(String name){return LauncherUpdates.home().resolve("skins/"+name+".png");}
    static BufferedImage read(Path f)throws Exception{
        if(!Files.isRegularFile(f)||Files.size(f)>1048576)return null;
        try(var input=ImageIO.createImageInputStream(f.toFile())){var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())return null;var r=readers.next();try{r.setInput(input);return r.getFormatName().equalsIgnoreCase("png")&&r.getWidth(0)==64&&(r.getHeight(0)==64||r.getHeight(0)==32)?r.read(0):null;}finally{r.dispose();}}
    }
    void load(Path game,String name){
        identity=name;
        new Thread(()->{BufferedImage texture=null;boolean thin=false;
            try(var jar=new java.util.zip.ZipFile(Frontier.mc().resolve("versions/1.21.1/1.21.1.jar").toFile())){
                var entry=jar.getEntry("assets/minecraft/textures/entity/player/wide/steve.png");
                if(entry!=null)try(var in=jar.getInputStream(entry)){texture=ImageIO.read(in);}
            }catch(Exception ignored){}
            try{
                boolean test=AccountPanel.test();String token=AccountPanel.token(test);
                if(token!=null&&name.matches("[A-Za-z0-9_]{1,24}")){
                    var data=AccountPanel.api(test,"/wardrobe/player/"+name,null,token);
                    if(data!=null&&data.has("skin")&&!data.get("skin").getAsString().isEmpty())
                        texture=ImageIO.read(new java.io.ByteArrayInputStream(Base64.getDecoder().decode(data.get("skin").getAsString())));
                }
            }catch(Exception ignored){}
            final var image=texture;final boolean model=thin;SwingUtilities.invokeLater(()->{if(identity.equals(name)){skin=image;slim=model;repaint();}});
        },"WoidWatch skin").start();
    }
    record V(double x,double y,double z){}
    V transform(double x,double y,double z,double a,double p){double u=x*Math.cos(a)+z*Math.sin(a),v=-x*Math.sin(a)+z*Math.cos(a);return new V(u,y*Math.cos(p)-v*Math.sin(p),y*Math.sin(p)+v*Math.cos(p));}
    void block(Graphics2D g,double cx,double cy,int w,int h,int d,int u,int v,double a,double p,Color color,double expand){
        double x=w/2.+expand,y=h/2.+expand,z=d/2.+expand;V[] pts=new V[8];
        for(int i=0;i<8;i++)pts[i]=transform((i%2==0?-x:x),((i&2)==0?-y:y),((i&4)==0?-z:z),a,p);
        int[][] faces={{4,5,6,u+d,v+d,w,h},{0,4,2,u,v+d,d,h},{5,1,7,u+d+w,v+d,d,h},{0,1,4,u+d,v,w,d},{6,7,2,u+d+w,v,w,d}};
        for(int j=0;j<faces.length;j++){int[] f=faces[j];V tl=pts[f[0]],tr=pts[f[1]],bl=pts[f[2]];
            double ax=tr.x-tl.x,ay=tr.y-tl.y,bx=bl.x-tl.x,by=bl.y-tl.y;if(ax*by-ay*bx<=.001)continue;
            var at=new AffineTransform(ax/f[5],ay/f[5],bx/f[6],by/f[6],cx+tl.x,cy+tl.y);
            var face=(Graphics2D)g.create();face.transform(at);
            if(skin!=null&&f[3]+f[5]<=64&&f[4]+f[6]<=skin.getHeight())face.drawImage(skin,0,0,f[5],f[6],f[3],f[4],f[3]+f[5],f[4]+f[6],null);
            else if(expand==0){face.setColor(j==0?color:color.darker());face.fillRect(0,0,f[5],f[6]);}
            if(skin!=null&&expand==0&&j!=0){face.setColor(new Color(0,0,0,40));face.fillRect(0,0,f[5],f[6]);}face.dispose();}
    }
    protected void paintComponent(Graphics gr){var g=(Graphics2D)gr.create();int w=getWidth(),h=getHeight();
        g.setPaint(new RadialGradientPaint(w*.5f,h*.55f,Math.max(1,h*.6f),new float[]{0,1},new Color[]{new Color(44,81,78),Look.INK}));g.fillRect(0,0,w,h);
        g.setColor(new Color(97,166,151,40));g.drawOval(w/2-49,h-21,98,13);g.drawOval(w/2-37,h-18,74,8);
        double s=Math.min((h-15)/39.,w/25.)*zoom;g.translate(w/2.,(h-31*s)/2);g.scale(s,s);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        int arm=slim?3:4;boolean modern=skin!=null&&skin.getHeight()==64;
        double body=studio?turn:-.10;
        boolean back=Math.cos(body)<0;
        if(cape!=null&&!back)g.drawImage(cape,-7,8,12,21,null);
        block(g,-2,26,4,12,4,0,16,body,0,Look.PANEL,0);block(g,2,26,4,12,4,modern?16:0,modern?48:16,body,0,Look.PANEL,0);
        block(g,0,14,8,12,4,16,16,body,0,Look.MINT,0);
        block(g,-4-arm/2.,14,arm,12,4,40,16,body,0,Look.MUTED,0);block(g,4+arm/2.,14,arm,12,4,modern?32:40,modern?48:16,body,0,Look.MUTED,0);
        if(modern){block(g,0,14,8,12,4,16,32,body,0,Look.MINT,.18);block(g,-4-arm/2.,14,arm,12,4,40,32,body,0,Look.MUTED,.18);block(g,4+arm/2.,14,arm,12,4,48,48,body,0,Look.MUTED,.18);block(g,-2,26,4,12,4,0,32,body,0,Look.PANEL,.15);block(g,2,26,4,12,4,0,48,body,0,Look.PANEL,.15);}
        block(g,0,4,8,8,8,0,0,body+yaw,pitch,Look.GOLD,0);
        if(skin!=null)block(g,0,4,8,8,8,32,0,body+yaw,pitch,Look.GOLD,.38);        if(cape!=null&&back)g.drawImage(cape,-5,8,10,16,null);
        if(hat!=null)g.drawImage(hat,-7,-5,14,Math.max(4,14*hat.getHeight()/hat.getWidth()),null);
        if(!back&&!pin.isEmpty()){
            g.setColor(pin.equals("amethyst")?new Color(196,142,250):new Color(231,148,96));
            g.fill(new Rectangle2D.Double(2,10,1.2,1.2));
        }
        if(studio&&!title.isEmpty()){
            g.setFont(Look.font(1,true));g.setColor(Look.GOLD);
            g.drawString(title,(float)(-g.getFontMetrics().stringWidth(title)/2.),-6);
        }
        g.dispose();}
}
