package ww;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class Closet extends Screen {
    final Screen parent;boolean shop,busy;JsonObject state;String message="";
    Closet(Screen parent){super(Component.literal("Гардероб WoidWatch"));this.parent=parent;}
    public void onClose(){minecraft.setScreen(parent);}
    protected void init(){if(state==null&&!busy)send(new JsonObject());else drawButtons();}
    void send(JsonObject body){
        if(busy)return;busy=true;message="Синхронизация…";drawButtons();
        Client.api("/desktop/wardrobe",body).whenComplete((result,error)->net.minecraft.client.Minecraft.getInstance().execute(()->{
            busy=false;if(error==null){state=result;message="";if(minecraft.player!=null)Client.next.remove(minecraft.player.getGameProfile().getName());}
            else message="Гардероб недоступен. Повторите вход в WoidWatch.";if(minecraft.screen==this)drawButtons();
        }));
    }
    Button button(String label,int x,int y,int w,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,18).build());}
    void drawButtons(){
        clearWidgets();int x=width/2-118;
        button("Инвентарь",x,26,116,()->{shop=false;drawButtons();});button("Магазин",x+120,26,116,()->{shop=true;drawButtons();});
        if(state!=null){int i=0;for(var c:Choice.rows(state,shop)){int col=i%2,row=i++/2;button(c.label(),width/2-152+col*153,48+21*row,151,()->send(c.request())).active=!busy&&!c.op().isEmpty();}}
        button("Назад",width/2-45,height-22,90,this::onClose);
    }
    public void render(GuiGraphics g,int mx,int my,float tick){super.render(g,mx,my,tick);g.drawCenteredString(font,title,width/2,10,0xffe6bb);g.drawCenteredString(font,message,width/2,height-34,0xffffff);}
}
