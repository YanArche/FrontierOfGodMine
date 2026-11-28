package ww.mixin;

import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import com.mojang.util.UndashedUuid;

@Mixin(value=ClientHandshakePacketListenerImpl.class, remap=false)
abstract class GameLoginMixin {
    @ModifyArgs(method="authenticateServer",at=@At(value="INVOKE",
        target="Lcom/mojang/authlib/minecraft/MinecraftSessionService;joinServer(Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;)V"))
    private void woidwatchGameTicket(Args args){
        String ticket=System.getenv("WW_GAME_TICKET");
        if(ticket!=null){
            args.set(0,UndashedUuid.fromStringLenient(System.getenv("WW_GAME_PROFILE")));
            args.set(1,ticket);
        }
    }
}
