# Первый сервис: контракт и проверки

## Реализация

`contract-model` — общий JAR без Spring/Servlet/Jetty, модели/DTO на Java records. `flats-service` — WAR с DispatcherServlet в web.xml, Java-конфигурацией Spring MVC, контроллерами, строгим decoder и хранилищем в памяти. Внешний Jetty — EE10/Servlet 6, context `/`.

Закреплены Java release 17, Spring 6.2.19, Jackson 2.20.2, Jetty 12.0.40. WAR не содержит Tomcat, Spring Boot или Jetty. `flats-service-jetty-errors.jar` содержит только JettyApiErrorHandler, устанавливается отдельно в JETTY_BASE/lib/ext и обеспечивает JSON-формат ранних ошибок контейнера, включая 414. Swagger UI/YAML копируются из существующего public/ в /docs/.

## Контракт

Источник: `public/flats.yaml`; сохранены все 9 операций. POST → 201 + `Location: /flats/{id}`, без тела; PUT/PATCH/GET → Flat; DELETE и экстремумы пустой коллекции → 204 без тела. Список → массив и X-Total-Count; пустая/запредельная страница → `[]`. Коллекция существует с запуска; неизвестный ID → 404 Error с сообщением об отсутствующей квартире.

POST/PUT/PATCH получают URL-encoded JSON в query flat. Входные id/creationDate запрещены. Дата — Clock Europe/Moscow; timestamp ошибки — Instant UTC. PUT сохраняет генерируемые поля, заменяет пользовательские; необязательные пропуски → null. PATCH сливает только присутствующие ключи, включая вложенные; пустой patch/вложенный patch запрещён. Ошибка не записывает частичных изменений.

Price — BigDecimal, минимум 0.01, кратность 0.01, без округления. Int64 не преобразуется через double. Неявные преобразования типов, неизвестные/повторные JSON-ключи запрещены. Структура, обязательные пропуски, null у non-nullable, неверные типы и переполнение → 400; ограничения допустимо сформированного объекта (включая значения enum вне набора) → 422. Любая ошибка фильтра → 400. Тело не заменяет параметры URL.

Фильтр — точное равенство и AND, вложенные объекты; null отличается от отсутствия. Затем сортировка и страница на одном снимке. Повторяемый sort: `sort=area:asc&sort=name:desc`; повтор поля → 400. Все пути/объекты поддерживаются; id:asc добавляется последним, если id отсутствует. Строки по Unicode code points, enum по объявлению, false перед true, null всегда последним. Page/size — положительные int32, defaults 1/20, без скрытого максимума size.

Счётчик различает `transport=%22NONE%22` и `transport=null`; некавыченный `transport=NONE` даёт 400.

Пример после запуска:

```powershell
$flat = '{"name":"Квартира","coordinates":{"x":30.31,"y":59.93},"area":54,"price":8500000.50,"numberOfRooms":2,"new":false,"view":"YARD","house":{"name":"Дом","numberOfFloors":5}}'
$encoded = [uri]::EscapeDataString($flat)
curl.exe --noproxy '*' --cacert .runtime/jetty-base/etc/flats.crt -i -X POST "https://localhost:8443/flats?flat=$encoded"
curl.exe --noproxy '*' --cacert .runtime/jetty-base/etc/flats.crt -i 'https://localhost:8443/flats?sort=area:desc&sort=name:asc&page=1&size=10'
```

## TLS и настройки

Сертификат RSA 3072/SHA256, SAN, serverAuth, срок 365 дней. Генераторы не перезаписывают ключи: для смены SAN/обновления использовать новую base-директорию. Клиент доверяет экспортированному etc/flats.crt; trust-all/отключение hostname verification не используются. Keystore, пароль и local-secrets.ini создаются только в runtime-каталоге. В Unix umask 077; в Windows использовать личный каталог с доступом своей учётной записи. Не публиковать runtime-каталог вместе с исходниками.

| Настройка | Default | Изменение |
| --- | --- | --- |
| JDK | JAVA_HOME, 17+ | Явно до сборки/запуска |
| Jetty home/base | .runtime/jetty-home-12.0.40, .runtime/jetty-base | prepare/start.ps1 -JettyHome/-JettyBase; sh — JETTY_HOME/JETTY_BASE |
| TLS port/bind | 8443 / 127.0.0.1 | prepare.ps1 -Port/-BindAddress; sh — FLATS_HTTPS_PORT/FLATS_BIND_ADDRESS |
| SAN | dns:localhost,ip:127.0.0.1 | prepare.ps1 -San; sh — FLATS_CERT_SAN, до генерации |
| TLS | TLSv1.2/TLSv1.3 | api-support.xml |
| Предел URI приложения | 16384 байт request target | FLATS_MAX_URI_BYTES либо JVM -Dflats.maxUriBytes, минимум 1024 |
| Лимит HTTP-парсера | 65536 байт | jetty.httpConfig.requestHeaderSize в base/start.d/https.ini |
| Heap | Xms64m / Xmx256m | Скрипт запуска с учётом квоты |

Лимиты приложения и парсера согласовывать. Длинный URI → 414 JSON; большие заголовки могут дать инфраструктурный 431. TLS-ошибки/повреждённые пакеты не являются JSON-ответами API. Модуль http не включён; HTTP на TLS-порту не обслуживается. Фильтр isSecure не доверяет пользовательскому X-Forwarded-Proto.

Для GET API включён CORS * без credentials и expose X-Total-Count/Location для будущего redirect агентства. Методы изменения cross-origin не открываются. Браузерная цепочка 303 относится к следующему этапу.

## Проверки

`mvn clean verify`: JUnit 5/MockMvc; разбор обеих OpenAPI со всеми ссылками/общими схемами и совпадение 9 method/path с контроллерами. CRUD/PATCH, обязательные/nullable поля, плохой JSON, статусы/UTC, большие числа, все пути сортировки/фильтрации, enum/Unicode/null-last, страницы/total, URL-кодирование и конкуренция.

`mvn verify -Pintegration` требует заранее запущенный Jetty. JettyHttpsIT проверяет все 9 операций через HTTPS, ошибки, оба уровня 414, отказ plaintext/недоверенного сертификата/неверного hostname, TLS 1.2/1.3. Доверяет только экспортированному сертификату; тестовые записи удаляются через API. Не запускать против чужого сервера с реальными данными. При остановленном сервере тесты завершаются ошибкой, не пропускаются.

Другой адрес/сертификат:

```powershell
mvn verify -Pintegration '-Dflats.baseUrl=https://localhost:9443' '-Dflats.certificate=C:/path/to/flats.crt'
```

XML-результаты: contract-model/target/surefire-reports, flats-service/target/surefire-reports, flats-service/target/failsafe-reports. Helios, Payara и пользовательский клиент на этом этапе не проверяются. Фактический результат финального прогона фиксируется ниже.

## Фактический результат, 12.10.2026

- `mvn clean verify` — BUILD SUCCESS: 41 проверка общего модуля и 47 проверок первого сервиса, без ошибок/пропусков.
- `mvn verify -Pintegration` — BUILD SUCCESS: те же 88 проверок и 9 реальных HTTPS-проверок JettyHttpsIT; итого **97**, failures/errors/skipped = 0.
- Среда: Windows 11, Maven 3.9.9, JDK 17.0.8; Jetty 12.0.40 запущен через поставленный start.ps1. prepare.ps1 проверен, включая повторную подготовку с сохранением сертификата. PowerShell AST разобрал все 3 скрипта без синтаксических ошибок.
- curl с `--cacert` вернул 200, Server: Jetty(12.0.40), X-Total-Count и JSON. Swagger index также вернул 200. У Java-процесса найден только listener 127.0.0.1:8443; http://localhost:8080/flats недоступен (curl exit 7), plaintext на 8443 отвергается интеграционным тестом.
- Проверены TLS 1.2 и 1.3, отказы для недоверенного сертификата/неверного hostname, 414 JSON при request target 20К и 100К для POST/PUT/PATCH. Проверка сертификата не отключалась.
- Проверено содержимое WAR: без Tomcat/Jetty/Spring Boot; существующие Swagger/YAML включены. Классы/библиотеки развёрнутого WAR и класс адаптера совпадают с финальной сборкой. git diff --check прошёл; ключи/пароли игнорируются git, YAML не изменены.
- Shell-файлы проверены на LF, но Unix/Helios-скрипты не выполнялись. Совместимость с реальной средой Helios остаётся непроверенной. После проверки локальный тестовый процесс остановлен, порт освобождён.
