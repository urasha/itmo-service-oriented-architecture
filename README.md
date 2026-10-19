# Service-oriented architecture

Лабораторная №2, вариант 74611. Первый сервис: Spring MVC 6.2.19 / Jetty 12.0.40; второй: JAX-RS 3.1 / Payara Server 6.2025.10. Java 17, оба REST API используют HTTPS. Веб-клиент со всеми 11 операциями включён в WAR Jetty.

## Состав

- `public/flats.yaml`, `public/agency.yaml` — исходные OpenAPI; `public/` — Swagger UI.
- `contract-model/` — неизменяемые модели/DTO, JSON и валидация.
- `flats-service/` — Spring MVC WAR и тесты.
- `agency-service/` — JAX-RS WAR, HTTPS REST-клиент первого сервиса и тесты.
- `client/` — HTML/CSS/JavaScript: формы, точные числа, фильтры, сортировки, пагинация; без runtime Node и сборки фронтенда.
- `deploy/jetty/`, `deploy/tls/` — конфигурация Jetty и генерация TLS.
- `docs/FIRST_SERVICE.md`, `docs/HELIOS_DEPLOYMENT.md` — подробности запуска и проверок.
- `deploy/payara/`, `docs/AGENCY_SERVICE.md` — подготовка Payara, truststore, запуск обоих сервисов и интеграционные проверки.
- [docs/CLIENT.md](docs/CLIENT.md) — использование и проверки клиента/Swagger; [docs/HELIOS_RUNBOOK.md](docs/HELIOS_RUNBOOK.md) — файлы для переноса и полные команды Helios.

## Локальный запуск (Windows PowerShell)

Нужны JDK 17+ и Maven 3.9.x. Из корня проекта:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17' # замените на путь своего JDK
mvn clean verify
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./deploy/jetty/prepare.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./deploy/jetty/start.ps1
```

prepare.ps1 скачивает закреплённый Jetty при необходимости (с проверкой SHA-512), создаёт отдельную `.runtime/jetty-base`, самоподписанный сертификат с SAN localhost/127.0.0.1 и копирует WAR. ExecutionPolicy действует только на запускаемый процесс PowerShell. Повторная подготовка сохраняет сертификат; выполнять при остановленном сервере. Ключи/пароли остаются в игнорируемой `.runtime/`.

Клиент: **<https://localhost:8443/app/index.html>**. API: <https://localhost:8443/flats>. Swagger: <https://localhost:8443/docs/index.html>. Запуск Payara — [docs/AGENCY_SERVICE.md](docs/AGENCY_SERVICE.md). Браузер должен доверять обоим публичным сертификатам. Клиент и Swagger используют `/app/config.js`, который содержит публичные HTTPS URL; prepare.ps1 Jetty по умолчанию задаёт агентство https://localhost:8444. Для других адресов — параметры -FlatsPublicUrl/-AgencyPublicUrl, для POSIX — FLATS_PUBLIC_BASE_URL/AGENCY_PUBLIC_BASE_URL.

Во втором терминале, с проверкой сертификата:

```powershell
curl.exe --noproxy '*' --cacert .runtime/jetty-base/etc/flats.crt -i https://localhost:8443/flats
mvn verify -Pintegration
```

Ожидается 200, `Server: Jetty(12.0.40)`, `X-Total-Count` и JSON-массив. Integration-профиль проверяет заранее запущенный сервер. Остановка: Ctrl+C в первом терминале. На Unix использовать prepare.sh/start.sh; см. инструкцию Helios.

## Операции

- `GET /flats` — фильтры, сортировки, страницы.
- `POST /flats`, `GET /flats/{id}`, `PUT /flats/{id}`, `PATCH /flats/{id}`, `DELETE /flats/{id}`.
- `GET /flats/min-creation-date`, `GET /flats/max-new`, `GET /flats/count-by-transport`.
- `GET /agency/get-cheapest/{id1}/{id2}`, `GET /agency/get-most-expensive/{id1}/{id2}/{id3}` — на Payara; данные запрашиваются у Jetty по HTTPS.

POST/PUT/PATCH получают JSON в URL-параметре `flat`; тело не используется. `filter` и `transport` также соответствуют YAML. Поле price обязательно по существующей спецификации, хотя его не было в исходном Java-классе ЛР1. Ошибки `{timestamp, message}`, UTC: структура → 400, ограничения модели → 422, отсутствующий ID → 404, длинный URI → 414.

Коллекция хранится в памяти и сбрасывается при рестарте. HTTP-коннектор не включён; servlet-фильтр дополнительно отвергает незащищённые запросы. Развёртывание на Helios не выполнялось.

## Проверки и перенос

```powershell
node --test client/test/*.test.js
mvn verify '-Pintegration,agency-integration' '-Dagency.certificate=C:/path/to/agency.crt'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/package.ps1 -IncludeRuntimes
```

132 Java-теста, 21 реальная HTTPS-проверка, 33 JS-теста и Chrome-проверка всех 11 операций/двух Swagger выполнялись локально. Подробности и намеренно отдельный upstream-unavailable тест — в docs/CLIENT.md. Пакет `.runtime/soa-74611-helios.zip` содержит WAR, deployment-сценарии, инструкции, SHA512SUMS и (с IncludeRuntimes) исходные архивы серверов. Секреты/готовые Windows-домены не включаются. Для переноса и запуска следуйте docs/HELIOS_RUNBOOK.md; Helios проверяет пользователь самостоятельно.
