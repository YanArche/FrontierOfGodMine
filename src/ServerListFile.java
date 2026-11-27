import java.io.*;
import java.nio.file.*;
import java.util.*;

final class ServerListFile {
    record Tag(int type, Object data) {}
    record Items(int type,List<Tag> values) {}
    static int count(DataInputStream in,int max)throws IOException{
        int n=in.readInt();if(n<0||n>max)throw new IOException("Недопустимый размер NBT");return n;
    }
    static Tag read(DataInputStream in,int type,int depth)throws IOException{
        if(depth>32)throw new IOException("Слишком глубокий NBT");
        Object data=switch(type){
            case 1,2,3,4,5,6 -> {int size=new int[]{0,1,2,4,8,4,8}[type];byte[] b=new byte[size];in.readFully(b);yield b;}
            case 7,11,12 -> {int size=type==7?1:type==11?4:8;byte[] b=new byte[count(in,8388608/size)*size];in.readFully(b);yield b;}
            case 8 -> in.readUTF();
            case 9 -> {int t=in.readUnsignedByte(),n=count(in,100000);var list=new ArrayList<Tag>();for(int i=0;i<n;i++)list.add(read(in,t,depth+1));yield new Items(t,list);}
            case 10 -> {var map=new LinkedHashMap<String,Tag>();int t;while((t=in.readUnsignedByte())!=0){String k=in.readUTF();if(map.put(k,read(in,t,depth+1))!=null)throw new IOException("Повтор ключа NBT");}yield map;}
            default -> throw new IOException("Неизвестный тип NBT");
        };return new Tag(type,data);
    }
    @SuppressWarnings("unchecked") static Map<String,Tag> fields(Tag tag)throws IOException{
        if(tag.type!=10)throw new IOException("Ожидался составной NBT");return (Map<String,Tag>)tag.data;
    }
    static void write(DataOutputStream out,Tag tag)throws IOException{
        switch(tag.type){
            case 8 -> out.writeUTF((String)tag.data);
            case 9 -> {Items items=(Items)tag.data;out.writeByte(items.type);out.writeInt(items.values.size());for(Tag x:items.values)write(out,x);}
            case 10 -> {for(var e:fields(tag).entrySet()){out.writeByte(e.getValue().type);out.writeUTF(e.getKey());write(out,e.getValue());}out.writeByte(0);}
            default -> {byte[] b=(byte[])tag.data;if(tag.type==7||tag.type==11||tag.type==12)out.writeInt(b.length/(tag.type==7?1:tag.type==11?4:8));out.write(b);}
        }
    }
    static synchronized void sync(Path game,String address)throws IOException{
        Path f=game.resolve("servers.dat"),marker=game.resolve(".frontier/community-server.txt");
        String previous=Files.exists(marker)?Files.readString(marker):"";
        if(address.isEmpty()&&previous.isEmpty())return;
        Tag root=new Tag(10,new LinkedHashMap<String,Tag>());String name="";
        if(Files.exists(f)){
            if(Files.size(f)>8388608)throw new IOException("Слишком большой servers.dat");
            try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(f)))){
                if(in.readUnsignedByte()!=10)throw new IOException("Неверный servers.dat");
                name=in.readUTF();root=read(in,10,0);if(in.read()!=-1)throw new IOException("Лишние данные в servers.dat");
            }
        }
        var map=fields(root);var tag=map.computeIfAbsent("servers",k->new Tag(9,new Items(10,new ArrayList<>())));
        if(tag.type!=9)throw new IOException("Неверный список серверов");Items list=(Items)tag.data;
        if(list.type==0&&list.values.isEmpty()){list=new Items(10,new ArrayList<>());map.put("servers",new Tag(9,list));}
        if(list.type!=10)throw new IOException("Неверный тип записи сервера");
        Tag owned=null;boolean existing=false;for(Tag item:list.values){
            var entry=fields(item);if(new Tag(8,address).equals(entry.get("ip")))existing=true;
            if(!previous.isEmpty()&&new Tag(8,previous).equals(entry.get("ip"))&&new Tag(8,"WoidWatch · Frontier of God").equals(entry.get("name")))owned=item;
        }
        if(owned!=null&&address.equals(previous))return;
        if(owned!=null)list.values.remove(owned);
        boolean add=!existing&&!address.isEmpty();
        if(add){var entry=new LinkedHashMap<String,Tag>();entry.put("name",new Tag(8,"WoidWatch · Frontier of God"));entry.put("ip",new Tag(8,address));list.values.add(0,new Tag(10,entry));}
        if(owned!=null||add){
            Files.createDirectories(game);Path tmp=Files.createTempFile(game,"servers-",".tmp");
            try{try(var out=new DataOutputStream(Files.newOutputStream(tmp))){out.writeByte(10);out.writeUTF(name);write(out,root);}
                if(Files.exists(f))Files.copy(f,f.resolveSibling("servers.dat.woidwatch-backup"),StandardCopyOption.REPLACE_EXISTING);
                Pack.move(tmp,f);
            }finally{Files.deleteIfExists(tmp);}
        }
        Pack.write(marker,add?address:"");
    }
}
