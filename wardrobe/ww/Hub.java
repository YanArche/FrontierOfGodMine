package ww;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

class Hub extends Screen {
    final Screen parent;
    Hub(Screen parent){this(parent,"Менеджер WoidWatch");}
    Hub(Screen parent,String name){super(Component.literal(name));this.parent=parent;}
    static Button button(String text,int x,int y,int width,Runnable action){
        return new Button(x,y,width,18,Component.literal(text),b->action.run(),DEFAULT_NARRATION){
            @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float tick){
                int x=getX(),y=getY(),w=getWidth(),h=getHeight();
                int edge=isFocused()?0xffe5c48e:isHovered()?0xff70d2b9:0xff446269;
                int fill=!active?0xff172427:isHovered()?0xff295050:0xee20383e;
                g.fill(x,y,x+w,y+h,edge);g.fill(x+1,y+1,x+w-1,y+h-1,fill);
                g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,getMessage(),
                    x+w/2,y+(h-8)/2,active?0xffe5f2ef:0xff8a9a9a);
            }
        };
    }
    private static final Button.CreateNarration DEFAULT_NARRATION=java.util.function.Supplier::get;
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override protected void init(){
        int x=width/2-98;
        addRenderableWidget(button("Сетевая игра",x,44,196,()->minecraft.setScreen(new JoinMultiplayerScreen(this)))).active=minecraft.allowsMultiplayer();
        addRenderableWidget(button("Настройки игры",x,67,196,()->minecraft.setScreen(new OptionsScreen(this,minecraft.options))));
        addRenderableWidget(button("Открыть папку сборки",x,90,196,()->Util.getPlatform().openFile(minecraft.gameDirectory)));
        addRenderableWidget(button("Назад",width/2-45,height-22,90,this::onClose));
    }
    void renderPanel(GuiGraphics graphics,int mx,int my,float tick){
        graphics.fillGradient(0,0,width,height,0xe00b1b22,0xfa091216);
        graphics.fill(Math.max(4,width/2-170),5,Math.min(width-4,width/2+170),height-31,0xaa172e35);
        graphics.fill(Math.max(4,width/2-170),5,Math.min(width-4,width/2+170),6,0xff5eb7a3);
        super.render(graphics,mx,my,tick);
        graphics.drawCenteredString(font,title,width/2,12,0xffe9f4ee);
    }
    @Override public void render(GuiGraphics graphics,int mx,int my,float tick){renderPanel(graphics,mx,my,tick);}
}
