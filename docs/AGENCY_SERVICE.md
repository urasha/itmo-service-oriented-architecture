# Второй сервис: JAX-RS / Payara

Обе операции исходного `public/agency.yaml` реализованы в `agency-service`. Агентство получает квартиры только через HTTPS `GET /flats/{id}` первого сервиса. Общие DTO, строгий Jackson JSON codec и проверка структуры переиспользуются из `contract-model`. Зависимостей на `flats-service`, Spring, собственный Jersey, БД и кеш нет.

## API и ошибки

| Метод и путь | Результат |
|---|---|
| `GET /agency/get-cheapest/{id1}/{id2}` | Минимальный `price`; при равенстве цен — первая квартира |
| `GET /agency/get-most-expensive/{id1}/{id2}/{id3}` | Максимальный `price`; при равенстве — первая из максимальных |

Цена сравнивается как BigDecimal. Все ID проверяются до вызовов upstream: положительные десятичные int64. Если все ID равны, возвращается 303 без тела и обращения к Jetty. Location — **абсолютный публичный HTTPS URL первого сервиса** `/flats/{id}`. Это соответствует `uri-reference` YAML и работает между разными портами/узлами; существование квартиры проверит Jetty при переходе.

Обычный успех — 200 с полным Flat, включая `id`, `creationDate`, `new`, nullable `transport`. Ошибка содержит только `{timestamp, message}`, timestamp в UTC с `Z`.

| Ситуация | Статус |
|---|---|
| Неверный ID | 400 |
| Jetty вернул 404 | 404 |
| Отказ соединения, ошибка TLS, прочие статусы Jetty, upstream-редирект, неправильный Content-Type/JSON/модель/ID ответа | 502 |
| Истёк срок соединения/ответа, включая чтение тела | 504 |
| Неизвестный путь / неподдерживаемый метод | 404 / 405, JSON |
| Непредвиденная ошибка агентства | 500 без внутренних деталей |

Клиент не следует upstream-редиректам, не повторяет запросы и не хранит свою коллекцию. Выполняет последовательно два или три GET. Ответ проверяется полностью: обязательные/вложенные поля, типы, ограничения модели. Изменения квартир видны при следующем запросе агентства.

## Стек и сборка

Java release 17; Jakarta EE API 10.0.0 (`provided`, JAX-RS 3.1); Payara **Server Community 6.2025.10**; Jackson 2.20.2. Проверено на Windows 11 / Maven 3.9.9 / JDK 17.0.8. Реализацию Jakarta REST предоставляет Payara. В WAR — приложение, общая модель и Jackson. `payara-web.xml` задаёт context root `/`, локальный class loader для закреплённого Jackson. JAX-RS Application монтируется на `/agency`.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
mvn clean verify
```

Артефакт второго сервиса — `agency-service/target/agency-service.war`. Maven сам контейнеры не запускает.

## Локальный запуск Windows

Payara CLI в этой среде повреждала пути с кириллицей при создании домена/передаче настроек JVM. Поэтому **PayaraHome и DomainsDirectory на Windows должны иметь ASCII-путь**. Исходники/WAR можно оставить в текущем каталоге с кириллицей. Замените каталог примера на свой доступный для записи ASCII-путь.

Соберите проект, подготовьте Jetty и запустите его в отдельном терминале:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/jetty/prepare.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/jetty/start.ps1
```

Во втором терминале установите Payara (ZIP из Maven Central, проверка SHA-512) и создайте отдельный домен:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
$runtimeDirectory = 'C:\Users\Public\soa-74611-runtime'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/install.ps1 -OutputDirectory $runtimeDirectory
$env:PAYARA_HOME = "$runtimeDirectory\payara6"
$env:PAYARA_DOMAINS = "$runtimeDirectory\domains"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/prepare.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/manage.ps1 -Action start
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/manage.ps1 -Action deploy
```

Для установленной совместимой Payara задайте `PAYARA_HOME` напрямую; его корень содержит `glassfish/`. Windows start использует отдельную скрытую консоль, чтобы фоновый сервер не удерживал pipes PowerShell. Журнал: `$env:PAYARA_DOMAINS/agency/logs/server.log`.

Повторять prepare только при остановленном домене. Сертификат/пароли сохраняются. Ключ агентства импортируется в штатный PKCS12 keystore домена с сохранением системных aliases. ACL каталогов домена и `agency-private` ограничены текущим пользователем. Не делитесь этими каталогами и не помещайте их в git. Клиентам передавайте только публичные `.crt`.

По умолчанию: Jetty `127.0.0.1:8443`, агентство `127.0.0.1:8444`, admin Payara `127.0.0.1:18048`. HTTP listener 18080 отключён. WAR привязан только к virtual server `server`. Admin console не предоставляет операции агентства, но может возвращать HTML login page с 200 для произвольного пути. Штатные служебные порты Payara (JMS, IIOP, JMX, Hazelcast) выделяются через PortBase=18000; они не являются REST API. На Helios согласовать все порты.

Проверка HTTPS без `-k`:

```powershell
curl.exe --noproxy '*' --cacert .runtime/jetty-base/etc/flats.crt -i https://localhost:8443/flats
curl.exe --noproxy '*' --cacert "$env:PAYARA_DOMAINS/agency-private/tls/agency.crt" -i https://localhost:8444/agency/get-cheapest/1/1
```

Второй запрос возвращает 303, даже если квартира ещё не создана. Для сравнения разных квартир создайте их через `POST /flats?flat=<URL-encoded JSON>` и подставьте полученные ID. Для `curl -L` нужен PEM bundle обоих публичных сертификатов.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/payara/manage.ps1 -Action stop
```

Jetty останавливается Ctrl+C в его терминале; после рестарта его коллекция пуста.

## Конфигурация и TLS

`agency-private/agency.properties` — UTF-8 вне WAR. Путь задаётся `-Dagency.config=...` в domain.xml; альтернативно доступна переменная `AGENCY_CONFIG`. Образец: `deploy/payara/agency.properties.example`. Относительные пути разрешаются относительно файла.

| Настройка | Назначение |
|---|---|
| `flats.internalBaseUrl` | HTTPS context root Jetty, доступный с Payara |
| `flats.publicBaseUrl` | HTTPS context root Jetty для Location, доступный браузеру |
| `flats.trustStore` | PKCS12 с публичным сертификатом Jetty |
| `flats.trustStorePasswordFile` | Отдельный файл пароля truststore |
| `flats.connectTimeoutMillis` / `flats.requestTimeoutMillis` | По умолчанию 3000/5000; допустимо 1..300000 мс |
| `cors.allowedOrigin` | Единственный разрешённый HTTPS origin будущего клиента |

Адреса/порты не зашиты в Java. URL — HTTPS без credentials/query/fragment. Internal/public URL можно разделить при SSH-туннелях. Клиент использует собственные SSLContext/TrustManagerFactory; hostname verification HTTPS включена, глобальный truststore Payara не заменяется. Пароли не передаются в аргументах процессов. Trust-all и отключения проверки сертификатов нет.

Входящий сертификат агентства: отдельный RSA 3072 / SHA256withRSA, PKCS12, SAN localhost/127.0.0.1 по умолчанию, EKU serverAuth. `prepare.ps1 -San 'dns:ACTUAL_HOST,ip:ACTUAL_IP'` задаёт SAN при первой генерации. Для ротации используйте отдельный новый runtime/домен и обновите доверие клиентов. TLS listener разрешает 1.2/1.3; SSL3/TLS1.0/TLS1.1 отключены. Фильтр дополнительно отвергает незащищённые запросы, не доверяя X-Forwarded-Proto.

CORS: GET/OPTIONS для указанного origin, без credentials; Location открыт для чтения; заголовки применяются и к ошибкам. Пользовательский клиент и настройка Swagger UI для отдельного origin не входят в этот этап; YAML сохранены.

Параметры prepare.ps1: `-PayaraHome`, `-DomainsDirectory`, `-DomainName`, `-PortBase`, `-HttpsPort`, `-BindAddress`, `-San`, `-FlatsCertificate`, `-FlatsInternalUrl`, `-FlatsPublicUrl`, `-AllowedOrigin`. При смене PortBase задайте `-AdminPort` у manage.ps1 (`PortBase + 48`). Порты должны быть свободны; скрипты не останавливают чужие процессы.

## Проверки

`mvn clean verify`: **130 тестов** — 41 общей модели, 47 первого сервиса, 42 агентства (1 контракт, 12 бизнес-логика/конфигурация, 29 клиентские HTTPS-сценарии). HTTPS fixtures клиента проверяют 404/прочие статусы/редирект, повреждённый JSON/неполную модель, отказ соединения, тайм-ауты заголовков/тела, недоверенный сертификат и неверный SAN. Это проверки клиента, не замена Payara.

При заранее запущенных обоих сервисах:

```powershell
$flatsCertificate = (Resolve-Path .runtime/jetty-base/etc/flats.crt).Path
$agencyCertificate = "$env:PAYARA_DOMAINS/agency-private/tls/agency.crt"
mvn verify '-Pintegration,agency-integration' "-Dflats.certificate=$flatsCertificate" "-Dagency.certificate=$agencyCertificate"
```

integration: 9 реальных HTTPS-проверок Jetty. agency-integration: 12 проверок Payara↔Jetty — сравнения/обновления/BigDecimal/равенство, 303, 400/404/405, CORS, TLS 1.2/1.3, отказ недоверенному сертификату/неверному hostname/plaintext. Тесты создают и удаляют свои квартиры через API; не направляйте их на чужую коллекцию.

Отдельная проверка недоступности: остановите свой Jetty, оставив Payara работающей:

```powershell
mvn verify -Pagency-integration '-Dit.test=AgencyUnavailableIT' '-Dagency.expectUnavailable=true' "-Dagency.certificate=$agencyCertificate"
```

Проверяет 502 обеих операций и независимость валидации/303 от upstream. При обычном прогоне этот тест намеренно пропускается. Maven не останавливает серверы. Для другой среды: `flats.baseUrl`, `agency.baseUrl`, `flats.publicBaseUrl`, `agency.allowedOrigin`, `agency.httpPort`, `agency.adminPort` — параметры `-D` тестов.

Результаты — `*/target/surefire-reports`, `*/target/failsafe-reports`. Журналы проверки второго этапа в игнорируемой `.runtime/`: `final-build.log`, `final-integration.log`, `agency-unavailable.log`. POSIX-скрипты и Helios требуют проверки на целевой ОС. Веб-клиент/Swagger реализованы следующим этапом: docs/CLIENT.md; перенос — docs/HELIOS_RUNBOOK.md.

Фактически выполнено: clean verify — BUILD SUCCESS, 130/130; совместный verify с двумя профилями — BUILD SUCCESS, те же 130 и 21 реальный HTTPS-тест (9 Jetty + 12 Payara). Единственный пропуск — AgencyUnavailableIT, который отдельно выполнен с остановленным Jetty: 1/1, BUILD SUCCESS. Всего проверено 152 разных тестовых сценария, без failures/errors. Повторные prepare/start/deploy/stop Payara проверены; start возвращает управление терминалу. HTTP(S) проверялся также curl с --cacert, без обхода TLS.

Официальные основания совместимости: [Supported Platforms](https://docs.payara.fish/community/docs/6.2025.10/General%20Info/Supported%20Platforms.html), [HTTP/TLS administration](https://docs.payara.fish/community/docs/6.2025.10/Technical%20Documentation/Payara%20Server%20Documentation/General%20Administration/Administering%20HTTP%20Connectivity.html), [создание доменов](https://docs.payara.fish/community/docs/6.2025.10/Technical%20Documentation/Payara%20Server%20Documentation/General%20Administration/Administering%20Domains.html).
