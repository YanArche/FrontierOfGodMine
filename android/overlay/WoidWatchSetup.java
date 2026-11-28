package net.kdt.pojavlaunch;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class WoidWatchSetup {
    private static final String PROFILE = "1ee92718-7cf0-4b75-948c-a9a7a003f606";
    private static final String VERSION = "neoforge-21.1.267";
    private static final int MINT = 0xff85edcc;
    private static final String ARCHIVE = "woidwatch/Frontier-Android.zip";

    static void mount(LauncherActivity activity) {
        activity.findViewById(R.id.woidwatch_library).setOnClickListener(v -> open(activity));
    }

    private static void open(LauncherActivity activity) {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = new LinearLayout(activity);
        int pad = (int)(20 * activity.getResources().getDisplayMetrics().density);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad,pad,pad,pad);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xf21c383e,0xff0c171f});
        glass.setCornerRadius(pad);
        panel.setBackground(glass);
        ImageView scene = new ImageView(activity);
        try(InputStream cover = activity.getAssets().open("woidwatch/frontier.jpg")) {
            scene.setImageDrawable(Drawable.createFromStream(cover,null));
            scene.setScaleType(ImageView.ScaleType.CENTER_CROP);
            panel.addView(scene,new LinearLayout.LayoutParams(-1,pad*7));
        } catch(IOException ignored) {}
        TextView title = new TextView(activity);
        title.setText("Frontier of God");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0,pad/2,0,0);
        panel.addView(title);
        TextView status = new TextView(activity);
        status.setTextColor(0xffc1d8d2);
        status.setPadding(0,pad/3,0,pad/2);
        status.setText("Minecraft Java 1.21.1 · NeoForge 21.1.267\nМобильный профиль · 30 кадров/с");
        panel.addView(status);
        Button install = new Button(activity);
        install.setText("Подготовить сборку");
        install.setBackgroundTintList(ColorStateList.valueOf(MINT));
        install.setTextColor(0xff0c221d);
        panel.addView(install);
        install.setOnClickListener(v -> {
            install.setEnabled(false);
            dialog.setCancelable(false);
            status.setText("Установка файлов сборки…");
            java.util.concurrent.ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                try {
                    File game = installPack(activity);
                    activity.runOnUiThread(() -> {
                        if(activity.isDestroyed())return;
                        try{
                            selectProfile(game);
                            boolean ready = new File(Tools.DIR_HOME_VERSION,VERSION+"/"+VERSION+".json").isFile();
                            status.setText(ready ? "Профиль подготовлен. Откройте список профилей и выберите Frontier of God."
                                : "Файлы готовы. В установщике версий установите NeoForge 21.1.267 для Minecraft 1.21.1.");
                        }catch(RuntimeException error){status.setText("Не удалось сохранить профиль.");
                        }finally{install.setEnabled(true);dialog.setCancelable(true);}
                    });
                } catch(IOException|RuntimeException failure) {
                    activity.runOnUiThread(() -> {
                        status.setText(failure instanceof java.io.FileNotFoundException ?
                            "В этой сборке APK отсутствует пакет Frontier." : "Ошибка установки: "+failure.getMessage());
                        install.setEnabled(true);dialog.setCancelable(true);
                    });
                } finally {
                    executor.shutdown();
                }
            });
        });
        dialog.setContentView(panel);
        dialog.show();
        if(dialog.getWindow()!=null){
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            int available=activity.getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.min(available-pad*2,pad*26),-2);
        }
    }

    private static File installPack(LauncherActivity activity)throws IOException {
        java.nio.file.Path parent = new File(Tools.DIR_GAME_HOME,"WoidWatch").getCanonicalFile().toPath();
        Files.createDirectories(parent);
        java.nio.file.Path game = parent.resolve("frontier-of-god");
        if(Files.isRegularFile(game.resolve("frontier-mobile.json")))return game.toFile();
        if(Files.exists(game))throw new IOException("Папка профиля уже существует без манифеста.");
        java.nio.file.Path staging = Files.createTempDirectory(parent,"frontier-");
        long bytes=0;
        int count=0;
        try {
            try(ZipInputStream zip = new ZipInputStream(activity.getAssets().open(ARCHIVE))){
                byte[] buffer = new byte[65536];
                for(ZipEntry entry; (entry=zip.getNextEntry())!=null;){
                    if(++count>3000)throw new IOException("Слишком много файлов в пакете.");
                    String name=entry.getName();
                    java.nio.file.Path to=staging.resolve(name).normalize();
                    if(name.contains("\\")||!to.startsWith(staging)||to.equals(staging))throw new IOException("Некорректный путь в пакете.");
                    if(entry.isDirectory()){Files.createDirectories(to);continue;}
                    Files.createDirectories(to.getParent());
                    try(java.io.OutputStream out=Files.newOutputStream(to,java.nio.file.StandardOpenOption.CREATE_NEW)){
                        int read;
                        while((read=zip.read(buffer))!=-1){
                            bytes+=read;
                            if(bytes>2L*1024*1024*1024)throw new IOException("Превышен размер пакета.");
                            out.write(buffer,0,read);
                        }
                    }
                }
            }
            if(!Files.isRegularFile(staging.resolve("frontier-mobile.json"))||!Files.isDirectory(staging.resolve("mods")))
                throw new IOException("Неполный пакет Frontier.");
            Files.move(staging,game);
            return game.toFile();
        } finally {
            if(Files.exists(staging))try(java.util.stream.Stream<java.nio.file.Path> entries=Files.walk(staging)){
                for(java.nio.file.Path path:entries.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList()))Files.deleteIfExists(path);
            }
        }
    }

    private static void selectProfile(File game) {
        LauncherProfiles.load();
        if(!LauncherProfiles.mainProfileJson.profiles.containsKey(PROFILE)){
            MinecraftProfile profile = new MinecraftProfile();
            profile.name = "Frontier of God";
            profile.type = "custom";
            profile.lastVersionId = VERSION;
            profile.gameDir = game.getAbsolutePath();
            LauncherProfiles.mainProfileJson.profiles.put(PROFILE,profile);
            LauncherProfiles.write();
        }
    }
}
