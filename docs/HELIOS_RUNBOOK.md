# Перенос и запуск всех компонентов на Helios

Ниже приведена общая инструкция переноса. Фактическое развёртывание на Helios выполнено 21 октября 2026 года: FreeBSD, JDK 17, отдельный статический фронтенд и два HTTPS-сервиса. Рабочие адреса, порты, проверенные команды и особенности этой установки описаны в [HELIOS.md](HELIOS.md).

## 1. Что переносить

Минимальный комплект:

- flats-service/target/flats-service.war — первый API, веб-клиент и Swagger внутри WAR.
- flats-service/target/flats-service-jetty-errors.jar — ранние JSON-ошибки Jetty.
- agency-service/target/agency-service.war — второй API.
- deploy/ из сформированного пакета: POSIX-сценарии, шаблоны Jetty, ConfigureDomain.java, Smoke.java, versions.env, env.example.
- Серверные дистрибутивы **Jetty 12.0.40** и **Payara Server Community 6.2025.10**, если их ещё нет на Helios. Совместимый JDK устанавливается отдельно для ОС Helios; Windows JDK копировать нельзя.

Node, Maven, npm и Playwright на Helios не нужны. Два Java-файла deployment-утилит запускаются непосредственно JDK source launcher; отдельного приложения они не создают. Не переносите Windows-домен Payara, локальные keystore, пароли и готовый Jetty base: сертификаты/домен создаются на Helios.

В PowerShell из корня проекта:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
mvn clean verify
node --test client/test/*.test.js
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/package.ps1 -IncludeRuntimes
```

Результат: **.runtime/soa-74611-helios.zip**. IncludeRuntimes добавляет исходные ZIP серверов из .runtime, скачанные ранее при подготовке и проверенные по SHA-512. Если этих ZIP нет, предварительно выполнить deploy/jetty/prepare.ps1 и deploy/payara/install.ps1 либо положить проверенные архивы с именами из deploy/versions.env в .runtime. Без IncludeRuntimes получается небольшой пакет приложений/сценариев для уже установленных серверов. Внутри SHA512SUMS для проверки переноса; ваши локально созданные ключи, пароли и настроенные домены не включаются. Исходные ZIP дистрибутивов не изменяются; их штатный domain1 для приложения не используется.

Подставьте ваш SSH-адрес/порт; это шаблоны, а не известные параметры Helios:

```powershell
$sshTarget = 'LOGIN@HELIOS_HOST'
$sshPort = 'SSH_PORT'
scp.exe -P $sshPort .runtime/soa-74611-helios.zip "${sshTarget}:soa-74611-helios.zip"
ssh.exe -p $sshPort $sshTarget
```

## 2. Preflight и распаковка на Helios

В SSH-сессии, под своей учётной записью:

```sh
umask 077
uname -a
uname -m
java -version
ulimit -a
# Подставьте фактический путь к JDK для ОС/архитектуры Helios.
export JAVA_HOME=/path/to/compatible/jdk17
"$JAVA_HOME/bin/java" -version
"$JAVA_HOME/bin/java" --list-modules
mkdir -p "$HOME/soa-74611"
cd "$HOME/soa-74611"
"$JAVA_HOME/bin/jar" xf "$HOME/soa-74611-helios.zip"
sh deploy/helios/preflight.sh
```

Нужен полноценный JDK с jdk.compiler и java.net.http. Проверить квоту диска/памяти, разрешённые порты и доступность loopback. Payara heap — 512m, Jetty — 256m, плюс внеheap-память. Не продолжать, если квоты/платформа не подходят. Если доступен sha512sum: `sha512sum -c SHA512SUMS`.

Для пакета с runtime ZIP:

```sh
mkdir -p "$HOME/soa-74611-runtime"
cd "$HOME/soa-74611-runtime"
"$JAVA_HOME/bin/jar" xf "$HOME/soa-74611/runtimes/jetty-home-12.0.40.zip"
"$JAVA_HOME/bin/jar" xf "$HOME/soa-74611/runtimes/payara-6.2025.10.zip"
cd "$HOME/soa-74611"
cp deploy/env.example deploy/local.env
chmod 600 deploy/local.env
```

## 3. Настройки

Отредактируйте `deploy/local.env` любым текстовым редактором. Файл содержит обычные shell assignments и пути, паролей в нём нет. Обязательно заменить JAVA_HOME и примерные порты на назначенные вам.

Для режима SSH-туннелей:

| Значение | Что поставить |
|---|---|
| JAVA_HOME | Полный путь к совместимому JDK |
| SOA_RUNTIME | Личный каталог runtime, по умолчанию $HOME/soa-74611-runtime |
| FLATS_HTTPS_PORT / AGENCY_HTTPS_PORT | Разрешённые удалённые порты двух API |
| AGENCY_PORT_BASE | Свободная согласованная база служебных портов Payara |
| AGENCY_ADMIN_PORT | PortBase + 48 |
| FLATS_BIND_ADDRESS / AGENCY_BIND_ADDRESS | 127.0.0.1 |
| FLATS_PUBLIC_BASE_URL | https://localhost:8443 — адрес браузера через локальный туннель |
| AGENCY_PUBLIC_BASE_URL | https://localhost:8444 — адрес браузера через второй туннель |
| AGENCY_ALLOWED_ORIGIN | https://localhost:8443 — origin клиента |
| FLATS_INTERNAL_URL | https://localhost:УДАЛЁННЫЙ_ПОРТ_JETTY; доступен самому Helios |
| FLATS_PUBLIC_URL | То же, что FLATS_PUBLIC_BASE_URL, для Location агентства |

env.example уже связывает зависимые пути/внутренний URL. Клиент находится в первом WAR, поэтому его HTTPS listener — тот же Jetty. Важны три разных адреса: внутренний Jetty, публичный Jetty и публичная Payara. При других локальных портах поправьте оба публичных URL и CORS origin.

PortBase Payara резервирует также суффиксы +9 (debug, если включён), +37/+38/+39 (IIOP), +48 (admin), +49/+59 (Hazelcast), +66 (OSGi), +76 (JMS), +80 (отключаемый HTTP), +81 (исходный TLS, заменяется AGENCY_HTTPS_PORT), +86 (JMX). Согласовать доступность всего набора; один свободный TLS-порт недостаточен. Скрипт не убивает чужие процессы.

Для прямого доступа вместо туннелей нужны разрешённый bind address, действительный DNS/IP Helios в SAN **обоих** сертификатов и соответствующие публичные HTTPS URL/CORS. Если listener привязан только к внешнему IP, укажите доступные самому Helios URL с SAN hostname в FLATS_INTERNAL_URL и AGENCY_PROBE_URL (второй используется только smoke). Менять SAN до первой генерации. Никогда не подставлять публичный localhost-туннель во внутренний URL агентства.

## 4. Подготовка, запуск, диагностика и остановка

Из каталога распакованного пакета:

```sh
sh deploy/helios/prepare.sh
sh deploy/helios/start.sh
sh deploy/helios/smoke.sh
```

prepare создаёт отдельные Jetty base и домен Payara с HTTPS, самоподписанными SAN-сертификатами и отдельным truststore Jetty для агентства. HTTP listener приложения отключён; административный listener Payara остаётся только на loopback и не публикует WAR. Пароли создаются случайно в приватных файлах; глобальный truststore JVM не подменяется.

start оставляет Jetty через nohup, сохраняет свой PID, запускает и разворачивает Payara штатным asadmin, выполняет smoke. Отдельный frontend-процесс не требуется. Если шаг завершился ошибкой, ранее запущенный процесс может остаться работающим: сначала посмотреть журналы и выполнить stop, затем повторять. Повторный prepare выполнять только после stop.

Журналы и остановка:

```sh
set -a
. ./deploy/local.env
set +a
tail -n 80 "$JETTY_BASE/logs/stdout.log"
tail -n 80 "$PAYARA_DOMAINS/$AGENCY_DOMAIN/logs/server.log"
sh deploy/helios/stop.sh
```

stop обращается только к своему домену и сохранённому PID Jetty, проверяет владельца и -Djetty.base, не использует pkill java. После рестарта Jetty коллекция пуста — это выбранное хранилище в памяти, не ошибка deployment.

Smoke.java выполняет реальные проверяемые HTTPS-запросы: GET коллекции/клиента/конфигурации/Swagger, 303 с публичным Location, 404 несуществующей квартиры через агентство, отклонение plaintext на TLS-портах. curl и Maven на Helios не требуются. Браузерный CORS/доверие дополнительно проверить с рабочей станции.

## 5. Туннели и проверка в браузере

На рабочей станции в отдельном PowerShell-терминале, подставив реальные удалённые порты:

```powershell
$sshTarget = 'LOGIN@HELIOS_HOST'
$sshPort = 'SSH_PORT'
$remoteFlatsPort = 'JETTY_REMOTE_PORT'
$remoteAgencyPort = 'PAYARA_REMOTE_HTTPS_PORT'
ssh.exe -N -T -o ExitOnForwardFailure=yes -p $sshPort -L "8443:127.0.0.1:$remoteFlatsPort" -L "8444:127.0.0.1:$remoteAgencyPort" $sshTarget
```

Туннель должен оставаться открытым; локальные 8443/8444 должны быть свободны (остановите локальные тестовые контейнеры). TLS завершается в Jetty/Payara на Helios, проверка сертификатов остаётся включённой.

Скопируйте **только публичные сертификаты**, при стандартных каталогах env.example:

```powershell
scp.exe -P $sshPort "${sshTarget}:soa-74611-runtime/jetty-base/etc/flats.crt" .runtime/helios-flats.crt
scp.exe -P $sshPort "${sshTarget}:soa-74611-runtime/payara-domains/agency-private/tls/agency.crt" .runtime/helios-agency.crt
curl.exe --noproxy '*' --cacert .runtime/helios-flats.crt -i https://localhost:8443/flats
curl.exe --noproxy '*' --cacert .runtime/helios-agency.crt -i https://localhost:8444/agency/get-cheapest/1/1
```

Доверьте эти два сертификата в своём браузере/хранилище текущего пользователя (после проверки отпечатков по SSH). Откройте оба origin, затем **https://localhost:8443/app/index.html** и **https://localhost:8443/docs/index.html**. Не применяйте curl -k, trust-all или ignoreHTTPSErrors.

Проверьте создание квартиры, её GET/PUT/PATCH/DELETE, фильтры/сортировку/страницы, пустые результаты, ошибки, сравнение разных ID и одинаковых ID с переходом Payara→Jetty. В Swagger переключите сервис и выполните запрос. Успешный smoke на сервере не заменяет эту браузерную проверку.

Для полного локального JUnit HTTPS-прогона дополнительно направьте локальные 18448/18480 на административный/отключённый HTTP-порты **своего** домена (обычные туннели 8443/8444 уже открыты). В отдельном терминале:

```powershell
$remotePortBase = 18000 # замените на свой AGENCY_PORT_BASE
$remoteAdminPort = $remotePortBase + 48
$remoteHttpPort = $remotePortBase + 80
ssh.exe -N -T -o ExitOnForwardFailure=yes -p $sshPort -L "18448:127.0.0.1:$remoteAdminPort" -L "18480:127.0.0.1:$remoteHttpPort" $sshTarget
```

И в терминале исходного проекта на рабочей станции:

```powershell
mvn verify '-Pintegration,agency-integration' '-Dflats.certificate=.runtime/helios-flats.crt' '-Dagency.certificate=.runtime/helios-agency.crt' '-Dagency.httpPort=18480' '-Dagency.adminPort=18448'
```

HTTP-forward открывает локальный порт, но соединение с отключённым удалённым listener должно отклоняться. Не направляйте проверки на чужой локальный сервер. Вместо дополнительных туннелей достаточно основного smoke на Helios и ручной браузерной проверки. Автотесты изменяют коллекцию — запускайте только на своём учебном экземпляре.

## Статус проверки

На Helios прошли серверная проверка Smoke.java и браузерная проверка всех 11 операций через опубликованный статический клиент и Swagger. Фактические параметры установки и команды управления записаны в [HELIOS.md](HELIOS.md).
