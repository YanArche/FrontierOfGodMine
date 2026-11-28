package ww.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=Minecraft.class, remap=false)
abstract class MultiplayerMixin {
    @Inject(method="allowsMultiplayer",at=@At("RETURN"),cancellable=true)
    private void useWoidWatchMultiplayer(CallbackInfoReturnable<Boolean> result){
        if(System.getenv("WW_GAME_TICKET")!=null)result.setReturnValue(true);
    }
}
