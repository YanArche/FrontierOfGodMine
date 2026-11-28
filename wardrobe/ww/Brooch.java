package ww;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid="woidwatch_wardrobe",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class Brooch extends RenderLayer<AbstractClientPlayer,PlayerModel<AbstractClientPlayer>> {
    Brooch(PlayerRenderer renderer){super(renderer);}
    @SubscribeEvent public static void layers(EntityRenderersEvent.AddLayers event){
        for(var skin:event.getSkins()){PlayerRenderer renderer=event.getSkin(skin);renderer.addLayer(new Brooch(renderer));}
    }
    public void render(PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer player,float limb,float distance,float tick,float age,float head,float pitch){
        if(player.isInvisible()||player.isSpectator())return;
        var look=Client.lookup(player);if(look==null||look.accessory().isEmpty())return;
        Item item=look.accessory().equals("amethyst")?Items.AMETHYST_SHARD:Items.COPPER_INGOT;
        pose.pushPose();
        try{getParentModel().body.translateAndRotate(pose);pose.translate(.15,.19,-.22);pose.scale(.16f,-.16f,.16f);
            Minecraft.getInstance().getItemRenderer().renderStatic(item.getDefaultInstance(),ItemDisplayContext.FIXED,light,OverlayTexture.NO_OVERLAY,pose,buffers,player.level(),player.getId());
        }finally{pose.popPose();}
    }
}
