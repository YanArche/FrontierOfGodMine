package ww.mixin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ww.Client;
@Mixin(value=AbstractClientPlayer.class,remap=false)
abstract class SkinMixin {
    @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
    private void wardrobeSkin(CallbackInfoReturnable<PlayerSkin> out){
        var p=(AbstractClientPlayer)(Object)this;
        var look=Client.lookup(p);
        if(look!=null&&look.skin()!=null){
            var original=out.getReturnValue();
            out.setReturnValue(new PlayerSkin(look.skin(),null,original.capeTexture(),original.elytraTexture(),PlayerSkin.Model.WIDE,false));
        }
    }
}
