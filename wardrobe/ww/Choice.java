package ww;
import com.google.gson.*;
import java.util.*;

public record Choice(String label,String op,String item) {
    public static List<Choice> rows(JsonObject state,boolean shop){
        List<Choice> result=new ArrayList<>();
        for(var entry:state.getAsJsonObject("catalog").entrySet()){
            String id=entry.getKey(),slot=entry.getValue().getAsJsonArray().get(0).getAsString();
            String name=entry.getValue().getAsJsonArray().get(1).getAsString();
            boolean owned=state.getAsJsonArray("owned").contains(new JsonPrimitive(id));
            boolean worn=state.get(slot).getAsString().equals(id);
            if(shop) result.add(new Choice(name+(owned?" · Получено":" · Бесплатно"),owned?"":"claim",id));
            else if(owned) result.add(new Choice(name+(worn?" · Снять":" · Надеть"),worn?"remove":"equip",worn?slot:id));
        }
        if(!shop) for(String i:List.of("1","2")){
            result.add(new Choice("Сохранить комплект "+i,"save",i));
            if(state.getAsJsonArray("sets").contains(new JsonPrimitive(i)))result.add(new Choice("Надеть комплект "+i,"load",i));
        }
        return result;
    }
    public JsonObject request(){
        var body=new JsonObject();body.addProperty("op",op);body.addProperty("item",item);return body;
    }
}
