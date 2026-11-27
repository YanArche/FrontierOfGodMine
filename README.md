# FRONTIER OF GOD

**Текущий клиент WoidWatch 1.3 — локальная одиночная игра.** [Профиль, выбор ника и запуск](launcher/LOCAL.md). Описания предыдущих выпусков ниже относятся к историческим версиям.

[WoidWatch Launcher для Windows и macOS](https://github.com/YanArche/FrontierOfGodMine/releases/tag/launcher-v1.1.0) содержит библиотеку, импорт существующей папки и собственный подписанный канал обновления. [Переход с 1.0.0](launcher/MIGRATION.md). Версия модпака задаётся в pack/VERSION, версия приложения — в LauncherUpdates.java. Для выпуска приложения после сборки подпишите манифест tools/sign-launcher.ps1, загрузите файлы tools/publish.py с тегом launcher-vX.Y.Z и опубликуйте launcher/stable.json. Закрытый ключ хранится в личном хранилище сертификатов Windows пользователя выпуска.

Minecraft Java Edition 1.21.1 · NeoForge 21.1.267

Для сервера и исходного клиента 1.0.0 скачайте и полностью распакуйте [выпуск сборки](https://github.com/YanArche/FrontierOfGodMine/releases/tag/v1.0.0).

* **Windows x64** — откройте Frontier.exe, нажмите «Обновить и открыть Java Edition», затем «Играть» в официальном лаунчере. При первом входе авторизуйтесь в Microsoft с лицензией Java Edition. Перед следующим запуском закройте игру и официальный лаунчер.
* **macOS Intel и Apple Silicon** — откройте Frontier of God.app. Требуется официальный Minecraft Launcher. Приложение без подписи Apple.
* **Сервер Windows x64** — распакуйте архив в любую папку с правом записи. Prepare-server.cmd подготовит моды и конфигурацию в подпапке server. Start-server.cmd проверяет обновления, устанавливает NeoForge и запускает сервер после принятия владельцем Minecraft EULA в server/eula.txt. Память настраивается в server/user_jvm_args.txt. Для обновления работающего сервера завершите его командой stop и снова используйте Start-server.cmd.

Java 21 входит в каждый архив. Клиент и сервер проверяют общий манифест ветки main перед запуском. При недоступности манифеста используется локальная копия. При первом полном запуске NeoForge требуется интернет для установки библиотек. Счётчик показывает «День N» при входе и смене дня. Миры и списки доступа хранятся отдельно от обновляемых файлов сборки.

## Подготовка выпуска на Windows

tools/export.py экспортирует моды и настройки из профиля CurseForge по списку файлов базового дистрибутива. Затем соберите src/*.java JDK 21 с lib/gson.jar в dist/frontier.jar, скомпилируйте platform/Bootstrap.cs как dist/Frontier.exe (winexe, System.Windows.Forms, platform/frontier.ico). Поместите установщик NeoForge 21.1.267 в dist/loader.jar и выполните python tools/release.py. Скрипт проверяет SHA-256 комплектов Java и создаёт три ZIP в dist. Изменения pack/index.json и соответствующих файлов pack публикуются одним коммитом.

## Выпуск обновления только приложения

1. Увеличьте BUILD и VERSION в src/LauncherUpdates.java. BUILD возрастает при каждом выпуске.
2. С JDK 21 в PATH выполните tools/build-launcher.ps1 и tests/launcher.ps1. Экспорт модов для этого пути не требуется.
3. На компьютере с ключом выполните tools/sign-launcher.ps1 -Build N -Version X.Y.Z. Сверьте версию и номер сборки, зафиксируйте исходники, оставив новый launcher/stable.json вне опубликованного коммита до готовности артефакта.
4. Выполните python tools/publish.py launcher-vX.Y.Z --app-only. После успешной загрузки и сверки SHA-256 опубликуйте подписанный launcher/stable.json в main. Для выпуска полного дистрибутива используйте tools/build.ps1 и вызов публикации без --app-only.

Выпуски launcher-v* независимы от версии pack/VERSION. Для обновления встроенного Java runtime или самого стартового загрузчика подготовьте новый полный дистрибутив.

## Аккаунты сообщества и адрес сервера

[Комплект для домашнего сервера и обновление лаунчера 1.2.0](https://github.com/YanArche/FrontierOfGodMine/releases/tag/launcher-v1.2.0) · [Инструкция по Cloudflare, автозапуску и приглашениям](accounts/README.md).

Аккаунты обслуживает отдельный HTTPS-маршрут. Публикация игрового адреса выполняется в окне «Администрирование». Кнопка «Мой аккаунт» связывает браузер с лаунчером по одноразовому коду.


