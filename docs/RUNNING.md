# Локальный запуск

Нужны JDK 17, Maven 3.9+ и PowerShell. Node.js используется только для тестов клиента.
Команды выполняются из корня проекта. В IDE включите обработку аннотаций Lombok.

## Сборка и Jetty

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
mvn clean verify
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/jetty/prepare.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/jetty/start.ps1
```

Jetty работает в этом терминале; остановка — `Ctrl+C`.
Подготовка скачивает сервер, создаёт сертификат и копирует WAR в `.runtime/jetty-base/`.
Повторную подготовку выполняйте при остановленном сервере; сертификаты сохраняются.

## Payara

Во втором терминале:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$payaraRuntime = Join-Path (Resolve-Path '.runtime').Path 'payara-6.2025.10'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/install.ps1 -OutputDirectory $payaraRuntime
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/prepare.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/manage.ps1 -Action start
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/manage.ps1 -Action deploy
```

На Windows пути Payara и домена должны быть без кириллицы. Для другого расположения
задайте `PAYARA_HOME` (каталог с `glassfish/`) и `PAYARA_DOMAINS` (каталог доменов).
После изменения исходников пересоберите WAR и выполните `-Action deploy`.
Если нужно повторить `prepare.ps1`, сначала остановите домен.

Остановка:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/manage.ps1 -Action stop
```

## Открыть приложение

- Клиент: https://localhost:8443/app/index.html
- Swagger UI: https://localhost:8443/docs/index.html
- Первый API: https://localhost:8443/flats
- Второй API: https://localhost:8444/agency/get-cheapest/1/1

Оба сервиса используют самоподписанные сертификаты: браузер должен доверять каждому из них.
Первый сертификат — `.runtime/jetty-base/etc/flats.crt`, второй —
`.runtime/payara-domains/agency-private/tls/agency.crt`. Ключи и пароли находятся в `.runtime/`.
Коллекция хранится в памяти и очищается при перезапуске Jetty.

Адреса API для клиента и Swagger задаются через `/app/config.js`.
Для Jetty параметры подготовки — `-FlatsPublicUrl` и `-AgencyPublicUrl`.
Параметры Payara `-FlatsInternalUrl`, `-FlatsPublicUrl`, `-AllowedOrigin`
задают адрес межсервисного вызова, адрес редиректа и origin браузерного клиента.

## Проверки

```powershell
mvn verify
node --test client/test/*.test.js
```

Maven проверяет Java-код и обе спецификации OpenAPI, включая ссылки на схемы.
Для HTTPS-проверок сначала запустите оба сервера:

```powershell
$agencyCert = (Resolve-Path '.runtime/payara-domains/agency-private/tls/agency.crt').Path
mvn verify '-Pintegration,agency-integration' "-Dagency.certificate=$agencyCert"
```

Для нестандартных портов доступны свойства `flats.baseUrl`, `agency.baseUrl`,
`agency.adminPort`, `agency.httpPort`; для сертификата первого сервиса — `flats.certificate`.
Тест `AgencyUnavailableIT` запускается отдельно с `-Dagency.expectUnavailable=true`
и `-Dit.test=AgencyUnavailableIT`, когда Payara работает, а Jetty остановлен.

## Пакет для переноса

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/package.ps1
```

Результат — `.runtime/soa-74611-helios.zip`: WAR, адаптер ошибок Jetty, сценарии,
инструкции и контрольные суммы. Готовые домены и секреты в пакет не входят.
`-IncludeRuntimes` добавляет исходные архивы серверов, если оба ZIP лежат в `.runtime/`.
Работа на учебном сервере описана в [HELIOS.md](HELIOS.md).
