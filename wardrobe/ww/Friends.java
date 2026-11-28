package ww;

import com.google.gson.*;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class Friends extends Hub {
    record Section(String id,String name,String first,String op,String second,String alternate){}
    static final List<Section> SECTIONS=List.of(
        new Section("friends","Друзья","Пригласить","invite","Удалить","remove"),
        new Section("incoming","Входящие","Принять","accept","Отклонить","decline"),
        new Section("outgoing","Исходящие","Отменить","cancel",null,null),
        new Section("invites","Приглашения","Серверы","servers","Скрыть","dismiss"),
        new Section("sent","Отправленные","Отозвать","revoke",null,null),
        new Section("blocked","Блокировки","Разблокировать","unblock",null,null));
    JsonObject state;boolean busy;int tab,page;
    String note="",draft="";
    EditBox nickname;
    Friends(Screen previous){super(previous,"Друзья WoidWatch");}
    @Override protected void init(){layout();if(state==null&&!busy&&Client.ticket!=null)request("view","");}
    private void request(String op,String target){
        if(busy)return;
        if(!op.equals("view")&&!target.matches("[A-Za-z0-9_]{3,16}")){note="Введите ник WoidWatch (3–16 символов).";return;}
        busy=true;note="Синхронизация…";
        var body=new JsonObject();body.addProperty("op",op);body.addProperty("target",target);
        Client.api("/desktop/social",body).whenComplete((result,error)->minecraft.execute(()->{
            busy=false;
            if(error==null){state=result;note=op.equals("invite")?"Приглашение отправлено на 24 часа.":"";}
            else note="Действие недоступно. Проверьте ник и вход.";
            if(minecraft.screen==this)layout();
        }));
    }
    private void control(String label,int x,int y,int w,Runnable action){
        addRenderableWidget(button(label,x,y,w,action));
    }
    private void layout(){
        clearWidgets();
        int left=Math.max(5,width/2-152), available=Math.min(width-10,304), cell=available/3;
        for(int i=0;i<SECTIONS.size();i++){
            int choice=i;var section=SECTIONS.get(i);
            var b=button((tab==i?"• ":"")+section.name,left+(i%3)*cell,26+(i/3)*20,cell-3,()->{tab=choice;page=0;layout();});
            addRenderableWidget(b);
        }
        nickname=new EditBox(font,left,69,available-142,18,Component.literal("Ник WoidWatch"));
        nickname.setHint(Component.literal("Ник игрока"));nickname.setMaxLength(16);nickname.setValue(draft);nickname.setResponder(s->draft=s);addRenderableWidget(nickname);
        control("Добавить",left+available-139,69,65,()->request("request",draft));
        control("Блокировать",left+available-72,69,72,()->request("block",draft));
        var section=SECTIONS.get(tab);
        var names=state==null?new JsonArray():state.getAsJsonArray(section.id);
        int visible=Math.max(1,(height-146)/21);
        page=Math.min(page,Math.max(0,(names.size()-1)/visible));
        for(int index=page*visible;index<Math.min(names.size(),(page+1)*visible);index++){
            String name=names.get(index).getAsString();int y=91+(index-page*visible)*21;
            control(name,left,y,available-148,()->{nickname.setValue(name);setFocused(nickname);});
            control(section.first,left+available-145,y,75,()->{if(section.op.equals("servers")){if(minecraft.allowsMultiplayer())minecraft.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this));else note="Сетевая игра недоступна.";}else request(section.op,name);});
            if(section.second!=null)control(section.second,left+available-67,y,67,()->request(section.alternate,name));
        }
        if(page>0)control("‹",left,height-24,24,()->{page--;layout();});
        if((page+1)*visible<names.size())control("›",left+27,height-24,24,()->{page++;layout();});
        control("Обновить",width/2-72,height-24,69,()->request("view",""));
        control("Назад",width/2+2,height-24,69,this::onClose);
    }
    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        renderPanel(g,mx,my,tick);
        String message=Client.ticket==null?"Войдите через WoidWatch.":note;
        if(message.isEmpty()&&state!=null&&state.getAsJsonArray(SECTIONS.get(tab).id).isEmpty())message="Здесь пока пусто.";
        g.drawCenteredString(font,message,width/2,height-41,0xffb7d5d0);
    }
}
