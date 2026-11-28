package ww;
import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid="woidwatch_wardrobe",value=Dist.CLIENT)
public class Client {
    static final String ticket=System.getenv("WW_COSMETICS");
    static final String base="test".equals(System.getenv("WW_COSMETICS_MODE"))?"http://127.0.0.1:18787":"https://accounts.noboredgames.win";
    static final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    public record Look(ResourceLocation skin,String title,String accessory){}
    static final ResourceLocation steve=ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");
    static final Map<String,Look> looks=new HashMap<>();
    static final Map<String,Long> next=new HashMap<>();
    static final Set<String> pending=new HashSet<>();
    static CompletableFuture<JsonObject> api(String path,JsonObject body){
        if(ticket==null)return CompletableFuture.failedFuture(new IllegalStateException("Войдите через WoidWatch"));
        var req=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(8)).header("Authorization","Bearer "+ticket);
        if(body!=null)req.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        return http.sendAsync(req.build(),HttpResponse.BodyHandlers.ofString()).thenApply(r->{
            if(r.statusCode()!=200)throw new IllegalStateException("Гардероб недоступен (HTTP "+r.statusCode()+")");
            return JsonParser.parseString(r.body()).getAsJsonObject();});
    }
    public static Look lookup(AbstractClientPlayer player){
        var mc=Minecraft.getInstance();
        if(ticket==null||!mc.isSameThread())return null;
        String name=player.getGameProfile().getName();
        if(name==null||!name.matches("[a-zA-Z0-9_]{1,16}"))return null;
        if(next.getOrDefault(name,0L)<System.currentTimeMillis()&&pending.size()<8&&pending.add(name)){
            next.put(name,System.currentTimeMillis()+30000);
            api("/wardrobe/player/"+name,null).whenComplete((data,error)->mc.execute(()->{
                pending.remove(name);if(error!=null||!data.get("member").getAsBoolean())return;
                try{ResourceLocation loc=steve;String png=data.get("skin").getAsString();
                    if(!png.isEmpty()){byte[] raw=Base64.getDecoder().decode(png);loc=ResourceLocation.fromNamespaceAndPath("woidwatch_wardrobe","skin/"+name.toLowerCase(Locale.ROOT)+"/"+Integer.toUnsignedString(Arrays.hashCode(raw)));
                        if(!looks.containsKey(name)||!loc.equals(looks.get(name).skin()))mc.getTextureManager().register(loc,new DynamicTexture(NativeImage.read(raw)));
                    }
                    var old=looks.put(name,new Look(loc,data.get("title").getAsString(),data.get("accessory").getAsString()));
                    if(old!=null&&!old.skin().equals(loc)&&!old.skin().equals(steve))mc.getTextureManager().release(old.skin());
                }catch(Exception ignored){}
            }));
        }return looks.get(name);
    }
    @SubscribeEvent public static void names(RenderNameTagEvent event){
        if(event.getEntity() instanceof AbstractClientPlayer p){var look=lookup(p);if(look!=null&&!look.title().isEmpty()&&p==Minecraft.getInstance().player&&!p.isInvisible()&&!p.isCrouching()&&!Minecraft.getInstance().options.getCameraType().isFirstPerson())event.setCanRender(net.neoforged.neoforge.common.util.TriState.TRUE);if(look!=null&&!look.title().isEmpty())event.setContent(event.getContent().copy().append(Component.literal(" · "+look.title()).withStyle(net.minecraft.ChatFormatting.GOLD)));}
    }
    @SubscribeEvent public static void menu(ScreenEvent.Init.Post event){
        if(event.getScreen() instanceof PauseScreen screen&&ticket!=null)event.addListener(Button.builder(Component.literal("Гардероб"),b->Minecraft.getInstance().setScreen(new Closet(screen))).bounds(4,4,74,20).build());
    }
}
