# Работа с лабораторной на Helios

Клиент: https://se.ifmo.ru/~s413022/soa-lab2/app/index.html

Swagger: https://se.ifmo.ru/~s413022/soa-lab2/docs/index.html

Статические файлы находятся в `~/public_html/soa-lab2/`. Оба Java-сервиса работают на Helios: Jetty — `127.0.0.1:32022`, Payara — `127.0.0.1:32023`. Из внешней сети прямое подключение к порту API не прошло, поэтому браузер обращается к ним через SSH-туннель.

## Открыть приложение

Из корня проекта в PowerShell:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/helios/tunnel.ps1
```

Введите SSH-пароль и оставьте терминал открытым. Скрипт соединяет `https://localhost:8443` с Jetty и `https://localhost:8444` с Payara на Helios. Эти локальные порты должны быть свободны.

Один раз откройте в том же браузере:

- https://localhost:8443/flats
- https://localhost:8444/agency/get-cheapest/1/2

Подтвердите исключения для двух самоподписанных сертификатов. У второго адреса JSON-ошибка «Квартира не найдена» допустима: это подтверждает доступность API. Публичные сертификаты также скачаны в `.runtime/helios-flats.crt` и `.runtime/helios-agency.crt`; приватные ключи остаются на Helios.

Затем откройте клиент по публичному адресу выше. Если Chrome запросит доступ страницы `se.ifmo.ru` к локальной сети, разрешите его: API доступны браузеру через `localhost`.

## Управление сервисами

```sh
ssh -p 2222 s413022@helios.cs.ifmo.ru
cd ~/soa-lab2

# Проверка работающих сервисов
sh deploy/helios/smoke.sh

# Остановка и повторный запуск
sh deploy/helios/stop.sh
sh deploy/helios/start.sh
```

Приложения и сценарии: `~/soa-lab2/`. Дистрибутивы, домен Payara, Jetty base и TLS: `~/soa-lab2-runtime/`. Настройки находятся в приватном `~/soa-lab2/deploy/local.env`. После остановки Jetty коллекция очищается, поскольку хранилище находится в памяти.

На сервере используется `/usr/local/openjdk17`, с ограничением начального heap для служебных Java-команд через `JAVA_TOOL_OPTIONS`. Payara разворачивает WAR через свой локальный административный REST API (`PAYARA_ADMIN_TRANSPORT=rest`): удалённые команды `asadmin` на этой JVM возвращали HTTP 400. Административный порт `32148` доступен только через loopback; API приложений обслуживаются по HTTPS.

Журналы:

```sh
tail -n 80 ~/soa-lab2-runtime/jetty-base/logs/stdout.log
tail -n 80 ~/soa-lab2-runtime/payara-domains/agency/logs/server.log
```

## Обновить статический фронтенд

Сборка здесь — подготовка HTML/CSS/JS и публичной конфигурации; клиент не использует транспиляцию.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File deploy/build-client.ps1
scp.exe -P 2222 -r .runtime/client-dist/app .runtime/client-dist/docs .runtime/client-dist/index.html s413022@helios.cs.ifmo.ru:public_html/soa-lab2/
```

Для обновления бэкенда сначала соберите WAR командой `mvn clean verify`, остановите свои сервисы, перенесите `flats-service.war`, `flats-service-jetty-errors.jar` и `agency-service.war` в одноимённые каталоги `~/soa-lab2/*-service/target/`, затем выполните `sh deploy/helios/prepare.sh` и `sh deploy/helios/start.sh` из `~/soa-lab2`.

## Проверено 21 октября 2026 года

- Локальная Maven-сборка: 140 тестов; клиент: 33 теста.
- Обе спецификации проверены OpenAPI-парсером.
- Серверная проверка HTTPS, связи Payara → Jetty, редиректа и отклонения HTTP.
- Chrome: все 11 операций через опубликованный статический клиент, ошибки, фильтры, сортировка, пагинация и обе спецификации Swagger. Тестовые записи удалены.

При браузерной проверке были разрешены доступ к локальной сети для `se.ifmo.ru` и исключения только для публичных ключей двух созданных сертификатов. Общая проверка HTTPS оставалась включённой.
