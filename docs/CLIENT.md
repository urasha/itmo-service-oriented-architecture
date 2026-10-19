# Веб-клиент и Swagger

Клиент: **https://localhost:8443/app/index.html**, Swagger: **https://localhost:8443/docs/index.html**. Статические HTML/CSS/ES modules упакованы в WAR первого сервиса; отдельные Node-сервер, npm install и сборка фронтенда для эксплуатации не нужны.

## Возможности

- Все 11 операций двух исходных OpenAPI: список, создание, GET по ID, PUT, PATCH, DELETE, оба экстремума, счётчик по транспорту, оба сравнения агентства.
- Таблица с раскрытием всех полей. Форма создания/замены, загрузка по ID, подтверждение удаления. Для PATCH у каждого поля есть «не менять» / «задать», у nullable-полей также «null». Доступен отдельный ввод JSON; ID и creationDate в него не добавляются формой.
- Точные AND-фильтры для всех 18 скалярных/объектных путей. Null и NONE различаются. Повторяемые сортировки с приоритетом и направлением, переключение страниц и размера, X-Total-Count. После применения новых условий — страница 1; пустая и запредельная страницы отображаются без ошибки.
- ID, area, цена и остальные числа читаются/передаются без округления через JS Number. `json-numbers.js` разбирает JSON по токенам и сохраняет числовые лексемы. Строки с цифрами/кавычками не изменяются. Цена не округляется и не получает ограничения MAX_SAFE_INTEGER.
- POST/PUT/PATCH используют query flat; filter/transport также идут в URL через URLSearchParams, с одним кодированием. После 201 клиент выполняет GET по Location; 204 не разбирается как JSON. Cross-origin 303 агентства автоматически приводит к GET первого сервиса, клиент показывает итоговую квартиру и факт перехода.
- Видны HTTP-код, сообщение ошибки и UTC-время сервиса. Для сетевого/TLS/CORS сбоя без доступного ответа причина не выдумывается. Тайм-аут 20 секунд охватывает чтение тела; ввод после ошибки сохраняется. Имена/сообщения выводятся через textContent, HTML из данных не исполняется.

## Запуск и публичные адреса

Сначала собрать оба WAR и запустить серверы по docs/AGENCY_SERVICE.md. Последний prepare.ps1 Jetty уже включает клиент:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
mvn clean verify
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/jetty/prepare.ps1 -AgencyPublicUrl https://localhost:8444
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/jetty/start.ps1
```

Payara должна иметь cors.allowedOrigin=https://localhost:8443 и публичный URL первого сервиса для Location=https://localhost:8443. Браузер должен доверять обоим самоподписанным сертификатам; сначала откройте оба HTTPS-origin. JavaScript не отключает проверку сертификатов. Первым сервисом клиент обслуживается с того же origin, поэтому CRUD не требует открытия CORS для произвольных сайтов.

`GET /app/config.js` — техническая конфигурация, содержит только три публичных значения, без внутренних адресов/truststore/паролей; Cache-Control:no-store. Параметры Jetty задаются при prepare или до запуска JVM:

| Параметр JVM | Переменная среды | Назначение |
|---|---|---|
| soa.flatsPublicBaseUrl | FLATS_PUBLIC_BASE_URL | URL первого сервиса; пустое значение означает origin страницы |
| soa.agencyPublicBaseUrl | AGENCY_PUBLIC_BASE_URL | Публичный HTTPS URL Payara |
| soa.clientTimeoutMillis | — | Общий тайм-аут одного браузерного запроса, по умолчанию 20000 мс |

prepare.ps1 пишет первые два значения в start.d/client.ini; параметры -FlatsPublicUrl и -AgencyPublicUrl. prepare.sh берёт одноимённые переменные среды; локальный default агентства — https://localhost:8444. После изменения конфигурации перезапустить Jetty. При увеличении серверных тайм-аутов агентства увеличить и браузерный тайм-аут с учётом трёх последовательных GET.

Swagger использует этот же config.js. Небольшой плагин добавляет servers в разобранные объекты обеих спецификаций; исходные YAML не изменены. Try it out квартиры/агентства обращается к соответствующему публичному origin. Пользовательский клиент сохраняет точные числа; просмотр больших чисел средствами самого vendored Swagger зависит от его штатного JSON renderer.

## Проверки

Обычные тесты клиента выполняются локально на Node 22, без пакетов:

```powershell
node --test client/test/*.test.js
```

Реальные браузерные тесты требуют **пустую отдельную коллекцию**, работающие Jetty/Payara, установленный Chrome и тестовый Playwright. Они создают и удаляют только свои записи; первый запуск после старта чистого Jetty удобен для проверки 204/пустой коллекции.

```powershell
npm.cmd install --prefix .runtime/browser-check --no-audit --no-fund playwright@1.64.1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/check-client.ps1 -AgencyCertificate "$env:PAYARA_DOMAINS/agency-private/tls/agency.crt"
```

check-client.ps1 временно добавляет два публичных сертификата в Root текущего пользователя через certutil, затем в finally удаляет только записи, добавленные им. Уже доверенные сертификаты сохраняются. Проверка сертификатов в Chrome включена; ignoreHTTPSErrors=false. Для Edge задайте SOA_BROWSER_CHANNEL=msedge. Другие адреса передайте -FlatsBaseUrl/-AgencyBaseUrl/-FlatsCertificate/-AgencyCertificate.

Фактически выполнено 19.10.2026:

- Java: 132 модульных/контрактных теста, включая два новых теста публичной конфигурации; 21 HTTPS-интеграционный тест настоящих Jetty/Payara. Агентство UnavailableIT намеренно пропускается при работающем Jetty; отдельная проверка недоступности описана в docs/AGENCY_SERVICE.md.
- Node: 33 теста пройдены — lossless JSON, nullable/PATCH, фильтры, int64/int32, URL-кодирование, ошибки/тайм-ауты.
- Chrome/Playwright: все 11 API-операций через интерфейс, точные большие цены/area, формы PUT/PATCH, null/NONE, 204, 400/404/414/422, сохранение ввода, nested AND, multi-sort, страницы/total/пустые выборки, настоящий cross-origin 303, XSS-строка, offline-сбой, desktop/mobile и реальные Execute в обеих спецификациях Swagger. Необработанных ошибок JavaScript нет.
- Java Smoke.java: клиент/Swagger/config, 303 Location, реальный вызов агентства к первому сервису, проверка TLS и отклонение plaintext; без curl и дополнительных библиотек.

Журналы: .runtime/client-build.log, client-final-integration.log, client-browser.log. Скриншоты: .runtime/screenshots/client-desktop.png и client-mobile.png. На Helios эти проверки не запускались; инструкции переноса — docs/HELIOS_RUNBOOK.md.
