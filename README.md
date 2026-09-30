# AdvancedModeratorGUI 2.7

GUI-плагин комплексной модерации для Paper 1.21.4+ с обязательной интеграцией LuckPerms.
Собран против Paper API 1.21.4 и работает на всех более новых сборках Paper.

## Что умеет

- наказания: kick, ban, tempban, IP-ban, mute, warn, freeze и массовые действия;
- карточка игрока, поиск, просмотр инвентаря/эндер-сундука и откат снимков;
- редактор групп, наследования и прав LuckPerms;
- жалобы, апелляции, доказательства и единые модераторские дела;
- AutoMod для чата, команд и табличек с нормализацией текста, исключениями и monitor mode;
- StaffChat, vanish, staff mode, trial-модераторы, шаблоны и бан-волны;
- SQLite или MySQL через HikariCP, версионируемые миграции и срок хранения логов;
- неизменяемый журнал аудита с SHA-256-цепочкой;
- read-only Web API с Bearer-аутентификацией и rate limit;
- русская и английская локализация через отдельные YAML-файлы.

## Новое в 2.7

- **совместимость с 1.21.4**: сборка переведена на Paper API `1.21.4-R0.1-SNAPSHOT` и Java 21
  (`api-version: 1.21`), все используемые API проверены на существование в 1.21.4;
- новый `me.admin.gui.compat.ServerCompat`: однократное определение платформы (Paper / Folia / Spigot)
  и версии Minecraft, кэшированные проверки классов, безопасный resolving `Material` и `Sound`
  без исключений, понятный отчёт о несовместимости в логе, `/amgui check` и `/amgui doctor`;
- звуки GUI теперь читаются из `config.yml` (`sounds.click/success/error`) и учитывают
  `gui.sound-enabled`: неизвестное или удалённое в новой версии имя звука молча подменяется
  встроенным дефолтом, результат кэшируется и сбрасывается командой `/amgui reload`;
- shaded-зависимости HikariCP и mysql-connector релоцируются в `me.admin.gui.libs.*`
  (sqlite-jdbc намеренно не релоцируется из-за JNI-символов);
- профили сборки: `paper-1.21.4` (по умолчанию), `paper-latest` (новый Paper API) и `quick` (без тестов);
- проверка окружения вынесена в `Doctor`: отдельные проверки `server-version` и `server-platform`;
- обновлён CI: сборка и тесты на JDK 21 и JDK 25, артефакт — `target/AdvancedModeratorGUI.jar`.

## Новое в 2.6

- единый fail-closed `ModerationActionService`: права, immunity, protected-цели, иерархия, rate limit и аудит повторно проверяются непосредственно перед действием;
- bounded `ActionReceipt` позволяет безопасно отменить последнее собственное BAN/TEMPBAN, MUTE, FREEZE или WARN; чужая отмена требует отдельного права;
- закрыт обход read-only инвентаря и эндер-сундука; StaffMode использует PDC-инструменты и те же security-проверки;
- автоматический WARN больше не превращается в BAN, а ручная эскалация требует отдельных прав;
- AuthMe не показывает хеши и не принимает пароли через чат; recovery/force actions требуют двух разных сотрудников;
- AutoMod async-путь работает по immutable контексту игрока, LuckPerms GUI не блокирует главный поток;
- audit enqueue стал неблокирующим и переводит критические действия в fail-closed при проблемах записи;
- общий bounded runtime executor, отмена callback после disable, runtime/context метрики и плановая privacy-очистка;
- все фоновые Bukkit-задачи имеют lifecycle owner; Doctor видит active task count, а conditional-компоненты различают `DISABLED` и `FAILED`;
- Triage Inbox объединяет дела, жалобы, апелляции и AutoMod approvals с claim/takeover и защитой конфликтов;
- Security Simulator проверяет AutoMod/AntiRaid без изменения живого состояния и наказаний;
- добавлены Incident Timeline/Snapshot, AutoMod Rules/Approvals GUI, role-aware dashboard и compact mode;
- legacy GUI переведены на типизированный `AMGuiHolder`, единые Back/Home/session и централизованный `CapabilityRegistry`;
- scoped Web API tokens с expiry и защитой от подбора; legacy token оставлен для совместимости;
- backup manifest и новые evidence подписываются HMAC; старые улики помечаются `LEGACY_UNSIGNED`;
- backup создаётся из staging-снимка одной filesystem generation и повторяется при конкурентной YAML-записи;
- сырые IP имеют retention, а долгосрочные связи аккаунтов сохраняются как ограниченные HMAC-идентификаторы;
- inventory snapshots/cache получили TTL и пределы per-player/total с защитой от resurrection старой записи;
- `/amgui privacy purge <игрок>` удаляет IP-историю и память чата игрока без обращения к базе данных.

## Новое в 2.5

- ролевой dashboard `/mod` с разделами игроков, расследований, наказаний, безопасности и системы;
- защищённые inventory GUI: заблокированы shift/hotbar/offhand/double-click переносы, исправлен lifecycle подтверждений;
- стек навигации сохраняет предыдущий экран, страницу и фильтры; чат-ввод поддерживает `cancel`/`отмена` и таймаут;
- raw IP доступен только с `amgui.viewip`, адреса маскируются в GUI и аудите, просмотры и поиск записываются;
- быстрый индекс связей аккаунтов по всей истории IP без повторного чтения `playerdata.yml`;
- AntiRaid считает уникальные UUID и подсети `/24`/`/64`, игнорирует неизвестные IP, поддерживает trusted age, cooldown, hysteresis и `alert-only`;
- AutoMod разделяет эскалацию по категории/тяжести, ограничивает regex-бюджет и отправляет `BAN/TEMPBAN` в YAML-очередь ручного одобрения;
- взвешенный circuit breaker наказаний с глобальным лимитом и жёстким cap даже для bypass;
- единый bounded/debounced YAML writer с atomic replace и обязательным flush при выключении;
- audit получил асинхронную bounded-очередь, HMAC-checkpoints, recovery-сегмент и health-метрики;
- улики проверяются canonical SHA-256 v2 и помечаются `VALID/INVALID`;
- бэкапы содержат SHA-256 manifest, поддерживают verify, preview, cancel и транзакционный rollback;
- restore по умолчанию требует одобрения второго сотрудника; инициатор не может одобрить собственный запрос;
- `/api/health` и `/amgui doctor` показывают очереди, кэши, ошибки webhook, аудит, YAML и улики;
- конфигурация валидируется до reload и откатывается при ошибке применения;
- добавлены детальные права просмотра/редактирования/экспорта/удаления и расширен security regression suite.

## Новое в 2.4

- единый центр расследования из головы игрока: риск-профиль, IP/GeoIP, исторические связи аккаунтов, жалобы, улики, дела, варны и заметки;
- объяснимая оценка риска 0–100, которая служит подсказкой и никогда не применяет наказания;
- модераторские сессии с таймером, автоматическим сбором действий из аудита и итоговой YAML-сводкой;
- AntiRaid по массовым входам, новым аккаунтам и одному IP: временный lockdown чата, ручное управление и YAML-журнал без автобанов;
- дела с дедлайнами, просроченными фильтрами, поиском, шаблонами и сохранением состояния GUI;
- защищённый ZIP-экспорт дела с жалобами, уликами, аудитом и `manifest.sha256`, без чтения базы данных;
- `/amgui doctor`: проверка YAML, аудита, GeoIP HTTPS, AutoMod, Web API, бэкапов, GUI и просроченных дел;
- подтверждённое восстановление YAML из бэкапа на следующем запуске с предварительной аварийной копией;
- GeoIP-проверка всех исторических IP, предупреждение о нескольких странах, расстояние и необязательные VPN/proxy/Tor/hosting поля;
- AutoMod-эскалация по настраиваемым ступеням и `automod-feedback.yml` для ложных срабатываний;
- очередь Discord webhook с HTTPS, таймаутами, ограничением размера и повторными попытками;
- чувствительные кнопки карточки игрока скрываются, если у модератора нет соответствующего права;
- быстрые kick/ban через Shift теперь также требуют подтверждения.

## Новое в 2.3

- GeoIP прямо в Minecraft: страна, регион, город, провайдер, часовой пояс и приблизительные координаты из карточки игрока, IP-истории или `/amgui geoip`;
- GeoIP выполняется асинхронно только по запросу сотрудника, приватные адреса блокируются, результаты кэшируются только в памяти;
- подробная карточка дела с обратной хронологией, ответственным, приоритетом и подтверждением закрытия/отклонения;
- автоматические и ручные ZIP-бэкапы YAML-файлов и `audit.log` с ротацией; база данных в них никогда не включается;
- усиленная нормализация AutoMod для смешанной кириллицы/латиницы, cooldown правил, лимит длины входа и отбрасывание подозрительных regex;
- команда добавления ложных срабатываний в исключения: `/amgui automod allow <текст>`;
- улучшенная навигация карточки игрока, IP-истории и дел.

## Новое в 2.2

- `cases.yml`: дело создаётся автоматически из жалобы; наказания автоматически прикрепляются к последнему открытому делу игрока;
- `audit.log`: важные действия записываются в цепочку, повреждение которой определяется при запуске;
- единая проверка безопасности наказаний: запрет действий над собой, защищёнными игроками и сотрудниками равного/старшего ранга;
- лимит массовых действий и те же проверки для шаблонов, trial-модерации и бан-волн;
- современный Paper Profile/IP Ban API;
- Adventure chat/title API вместо устаревших событий и методов;
- безопасный Web API: локальный bind по умолчанию, обязательный токен, CORS allowlist, security headers и ограничение запросов;
- AutoMod monitor mode, Unicode-нормализация, исключённые миры/домены/слова и фильтрация правил по группам;
- пул соединений HikariCP, индексы, миграции, пагинация запросов и очистка старых записей;
- Maven-тесты и GitHub Actions.

## Требования

- Paper `1.21.4` или новее (сборка идёт против Paper API `1.21.4-R0.1-SNAPSHOT`);
- Java 21+ (сервер 1.21.4 требует Java 21);
- LuckPerms 5.4+;
- опционально: Vault, PlaceholderAPI, AuthMe, ViaVersion, CoreProtect, Dynmap, WorldGuard, Simple Voice Chat.

Не поддерживается: Spigot/CraftBukkit (нужны Paper-only API — баны, профили, TPS) и Folia
(плагин использует `BukkitScheduler`; в `plugin.yml` указано `folia-supported: false`).

## Совместимость

| Параметр | Значение |
|---|---|
| `api-version` | `1.21` |
| Минимальная версия сервера | Minecraft 1.21.4 (Paper) |
| Целевой байткод | Java 21 (`maven.compiler.release=21`) |
| Проверка окружения | лог при старте, `/amgui check`, `/amgui doctor` |

Как обеспечивается совместимость:

1. **Один API-слой.** Весь код использует только API, существующий в Paper 1.21.4; сборка идёт
   против Paper API 1.21.4, поэтому появление более нового API сразу ломает билд, а не сервер.
2. **Детекция вместо предположений.** `ServerCompat` один раз определяет платформу и версию
   (`Bukkit.getMinecraftVersion()` → `getBukkitVersion()` → `getVersion()`), кэширует результат и
   проверяет наличие классов (`BanListType`, `PlayerProfile`, Adventure, Folia).
3. **Безопасные перечисления.** `Material`/`Sound` из конфига разбираются через
   `ServerCompat.material(...)`, `ServerCompat.sound(...)`: неизвестное имя даёт fallback,
   а не `IllegalArgumentException` и не `NoSuchFieldError` на другой версии.
4. **Мягкая деградация.** На сервере старше 1.21.4 или на Spigot плагин не падает: он пишет
   предупреждения в лог и отключает только те функции, которых нет в среде.
5. **Проверка в рантайме.** `/amgui doctor` показывает проверки `server-version` и
   `server-platform`, `/amgui check` — строку вида `PAPER 1.21.4`.

## Установка

1. Скопируйте `AdvancedModeratorGUI.jar` в каталог `plugins`.
2. Установите LuckPerms и запустите сервер.
3. Настройте `plugins/AdvancedModeratorGUI/config.yml` и `automod.yml`.
4. Выдайте права, например: `lp group admin permission set amgui.* true`.
5. Откройте панель командой `/mod`.

При обновлении старые конфиги не удаляются. Новые параметры имеют безопасные значения по умолчанию; при необходимости перенесите нужные секции из конфигов внутри JAR.

## Основные команды

| Команда | Назначение | Право |
|---|---|---|
| `/mod` | главная GUI-панель | `amgui.use` |
| `/mod player <ник>` | карточка игрока | `amgui.player` |
| `/mod inbox` | единая очередь задач | `amgui.inbox.view` |
| `/mod simulator` | GUI безопасной симуляции защиты | `amgui.simulator` |
| `/mod incident <ник>` | хронология инцидента | `amgui.incident.view` |
| `/mod snapshot <ник>` | YAML-снимок инцидента | `amgui.incident.snapshot` |
| `/sc` | StaffChat | `amgui.staffchat` |
| `/report <ник> <причина>` | отправить жалобу | `amgui.report.use` |
| `/appeal ...` | подать апелляцию | `amgui.appeal.use` |
| `/amgui cases` | список дел | `amgui.cases` |
| `/amgui case ...` | создать/изменить дело | `amgui.cases.edit` |
| `/amgui audit` | аудит и проверка целостности | `amgui.audit` |
| `/amgui automod test <текст>` | безопасно проверить правила | `amgui.automod.view` |
| `/amgui automod allow <текст>` | добавить ложное срабатывание в исключения | `amgui.automod.edit` |
| `/amgui automod pending/approve/reject` | очередь опасных действий AutoMod | `amgui.automod.approve` |
| `/amgui simulate automod <текст>` | dry-run AutoMod без наказания | `amgui.simulator` |
| `/amgui simulate raid <входы> <новые> <подсеть>` | dry-run AntiRaid | `amgui.simulator` |
| `/amgui privacy purge <игрок>` | удалить IP/history privacy-данные | `amgui.privacy.purge` |
| `/amgui geoip <игрок или IP>` | приблизительная GeoIP-информация | `amgui.geoip` + `amgui.viewip` |
| `/amgui backup create/list/verify` | создать, перечислить или проверить YAML-бэкап | `amgui.backup.create` / `.verify` |
| `/amgui backup preview/restore/cancel` | preview и транзакционное восстановление | `amgui.backup.restore` |
| `/amgui backup pending/approve/reject` | одобрение restore вторым сотрудником | `amgui.backup.restore` |
| `/amgui raid status/on/off` | AntiRaid и ручной lockdown | `amgui.antiraid` / `.toggle` |
| `/amgui doctor` | полный отчёт диагностики | `amgui.doctor` |
| `/amgui case template <игрок> <шаблон>` | создать дело из YAML-шаблона | `amgui.cases` |
| `/amgui case deadline <id> <срок>` | изменить дедлайн дела | `amgui.cases` |
| `/amgui case export <id>` | подписанный ZIP-экспорт дела | `amgui.cases.export` + `amgui.viewip` |
| `/amgui cleanup [дни]` | удалить старые DB-логи | `amgui.admin` |
| `/amgui language <ru|en>` | выбрать язык | `amgui.admin` |
| `/amgui reload` | перечитать конфигурацию | `amgui.admin` |

Подсказки для всех вариантов `/amgui case` выводятся командой `/amgui case`.

## Безопасность наказаний

```yaml
punishment-security:
  prevent-self-action: true
  enforce-hierarchy: true
  allow-equal-weight: false
  fail-closed-on-lookup-error: true
  punish-protection-violations: false
  bypass-permission: amgui.hierarchy.bypass
  mass-action-limit: 10
```

Иерархия определяется по weight основной группы LuckPerms. Право обхода следует выдавать только владельцам сервера. Консоль не ограничивается иерархией.

## Web API

API выключен по умолчанию. Для включения задайте случайный токен длиной не менее 16 символов:

```yaml
web-panel:
  enabled: true
  bind-address: 127.0.0.1
  port: 8080
  token: "replace-with-a-long-random-secret"
  cors-origin: ""
  rate-limit-per-minute: 120
```

Запрос: `Authorization: Bearer <token>`. Доступны `/api/stats`, `/api/players`, `/api/bans`, `/api/reports`, `/api/appeals`, `/api/cases`, `/api/audit`, `/api/health`. API только читает данные. Для внешней публикации оставляйте локальный bind и используйте HTTPS reverse proxy; внешний bind требует явного `web-panel.allow-external: true`.

В 2.6 рекомендуется вместо legacy `token` хранить только SHA-256 токена в `web-panel.tokens`. Доступные scope: `health`, `stats`, `moderation`, `audit`, `all`; для каждого токена можно задать `expires-at` в Unix epoch milliseconds. Пять неверных авторизаций за минуту создают одно агрегированное событие аудита без сохранения raw IP клиента.

## База данных

SQLite используется по умолчанию. Для MySQL настройте секцию `database.mysql`. Параметры пула находятся в `database.pool`, срок хранения — `database.retention-days`. Миграции применяются автоматически и отмечаются в таблице `schema_migrations`.

## AutoMod

Правила находятся в `automod.yml`. Сначала рекомендуется включить `settings.monitor-mode: true`, проверить срабатывания через `/amgui automod test`, заполнить `allowed-domains`, `allowed-words`, `ignored-worlds` и `ignored-commands`, а затем выключить monitor mode. Парольные команды (`/login`, `/register`, `/changepassword`, `/2fa` и аналоги) никогда не анализируются и редактируются в command-spy.

## GeoIP и приватность

`/amgui geoip <игрок|IP>` и кнопка с компасом в карточке игрока используют HTTPS API `ipwho.is`. Запрос отправляется только после действия сотрудника с правами `amgui.geoip` и `amgui.viewip`. GeoIP показывает местоположение диапазона адресов и не является точным домашним адресом; VPN, прокси и мобильные сети снижают точность. Интеграцию можно отключить через `ip-geolocation.enabled: false`.

Raw IP по умолчанию хранится 30 дней и ограничен 20 последними адресами игрока. Для долгосрочного поиска связанных аккаунтов используются HMAC-идентификаторы, из которых нельзя получить адрес напрямую. Срок и лимиты настраиваются в `privacy`; `/amgui privacy purge <игрок>` удаляет оба вида IP-данных и текущую память истории чата.

## YAML-бэкапы

Секция `backups` управляет созданием архивов при запуске, интервалом и числом хранимых копий. В ZIP входят только `.yml`, `.yaml` и `audit.log`; SQLite-файлы, MySQL и экспортированные логи исключены намеренно. Перед restore используйте `verify` и `preview`; staging можно отменить командой `cancel`, а сбой применения автоматически откатывается из emergency backup.

Новые архивы содержат `manifest.hmac`; при `backups.require-signature: true` восстановление неподписанного или изменённого архива отклоняется. Ключ `artifact-signing.key` специально не включается в архив: храните его отдельную защищённую копию, иначе после переноса сервера прежние подписи нельзя будет подтвердить.

## Локализация

Выберите `language: ru` или `language: en` в `config.yml`. Файлы `messages_ru.yml` и `messages_en.yml` создаются в каталоге плагина, поэтому тексты можно менять без пересборки.

## Сборка

```bash
# по умолчанию: Paper API 1.21.4, Java 21
mvn clean verify

# сборка против более нового Paper API (1.21.5+ / 1.26)
mvn -Ppaper-latest clean verify

# быстрый jar без тестов
mvn -Pquick clean package
```

Готовый файл: `target/AdvancedModeratorGUI.jar`. Требуется JDK 21+ (рекомендуется JDK 21 или 25).
Артефакт, собранный под 1.21.4, загружается и на более новых версиях Paper.
