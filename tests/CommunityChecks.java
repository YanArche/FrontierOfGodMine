import java.nio.file.*;
import java.util.Arrays;

class CommunityChecks {
    public static void main(String[] args)throws Exception{
        Path game=Files.createTempDirectory("ww-servers-");
        ServerListFile.sync(game,"");if(Files.exists(game.resolve("servers.dat")))throw new AssertionError();
        String user="private.example:25566";
        ServerListFile.sync(game,user);
        Path marker=game.resolve(".frontier/community-server.txt");Files.delete(marker);
        Path list=game.resolve("servers.dat");byte[] original=Files.readAllBytes(list);
        ServerListFile.sync(game,"frontier.example");
        byte[] once=Files.readAllBytes(list);ServerListFile.sync(game,"frontier.example");
        if(!Arrays.equals(once,Files.readAllBytes(list)))throw new AssertionError("duplicate");
        ServerListFile.sync(game,"[::1]:25566");ServerListFile.sync(game,"");
        if(!Arrays.equals(original,Files.readAllBytes(list)))throw new AssertionError("lost user server");
        for(String bad:new String[]{"https://example.net","a:99999","x:0","a/b"}){
            try{Community.address(bad);throw new AssertionError(bad);}catch(IllegalArgumentException expected){}
        }
        String key="a7d248_B-test";if(!WoidAccount.protect(WoidAccount.protect(key,true),false).equals(key))throw new AssertionError("vault");
        System.out.println("PASS: empty address, add/change/remove, player entries, idempotence, address validation, OS vault");
    }
}
