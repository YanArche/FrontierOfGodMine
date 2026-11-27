# FRONTIER OF GOD

[WoidWatch Launcher для Windows и macOS](https://github.com/YanArche/FrontierOfGodMine/releases/tag/launcher-v1.1.0) содержит библиотеку, импорт существующей папки и собственный подписанный канал обновления. [Переход с 1.0.0](launcher/MIGRATION.md). Версия модпака задаётся в pack/VERSION, версия приложения — в LauncherUpdates.java. Для выпуска приложения после сборки подпишите манифест tools/sign-launcher.ps1, загрузите файлы tools/publish.py с тегом launcher-vX.Y.Z и опубликуйте launcher/stable.json. Закрытый ключ хранится в личном хранилище сертификатов Windows пользователя выпуска.

Minecraft Java Edition 1.21.1 · NeoForge 21.1.267

Скачайте и полностью распакуйте [последний выпуск](https://github.com/YanArche/FrontierOfGodMine/releases/latest).

* **Windows x64** — откройте Frontier.exe, нажмите «Обновить и открыть Java Edition», затем «Играть» в официальном лаунчере. При первом входе авторизуйтесь в Microsoft с лицензией Java Edition. Перед следующим запуском закройте игру и официальный лаунчер.
* **macOS Intel и Apple Silicon** — откройте Frontier of God.app. Требуется официальный Minecraft Launcher. Приложение без подписи Apple.
* **Сервер Windows x64** — распакуйте архив в любую папку с правом записи. Prepare-server.cmd подготовит моды и конфигурацию в подпапке server. Start-server.cmd проверяет обновления, устанавливает NeoForge и запускает сервер после принятия владельцем Minecraft EULA в server/eula.txt. Память настраивается в server/user_jvm_args.txt. Для обновления работающего сервера завершите его командой stop и снова используйте Start-server.cmd.

Java 21 входит в каждый архив. Клиент и сервер проверяют общий манифест ветки main перед запуском. При недоступности манифеста используется локальная копия. При первом полном запуске NeoForge требуется интернет для установки библиотек. Счётчик показывает «День N» при входе и смене дня. Миры и списки доступа хранятся отдельно от обновляемых файлов сборки.

## Подготовка выпуска на Windows

tools/export.py экспортирует моды и настройки из профиля CurseForge по списку файлов базового дистрибутива. Затем соберите src/*.java JDK 21 с lib/gson.jar в dist/frontier.jar, скомпилируйте platform/Bootstrap.cs как dist/Frontier.exe (winexe, System.Windows.Forms, platform/frontier.ico). Поместите установщик NeoForge 21.1.267 в dist/loader.jar и выполните python tools/release.py. Скрипт проверяет SHA-256 комплектов Java и создаёт три ZIP в dist. Изменения pack/index.json и соответствующих файлов pack публикуются одним коммитом.
