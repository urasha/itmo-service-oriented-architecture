# Локальный стенд ЛР3

Сборка работает на Windows и Linux. Серверные сценарии рассчитаны на Linux, WSL 2 и FreeBSD. В PowerShell команды `sh` из этой инструкции можно запускать через `wsl -d Ubuntu -- sh`. Выполняйте команды из корня репозитория.

## Первый запуск

1. Соберите проект с JDK 17 и Maven 3.9+.

   ```sh
   mvn clean verify
   ```

2. В серверном окружении нужны JDK 17 с `keytool`, HAProxy с поддержкой OpenSSL, Python 3.8+, `openssl`, `curl` и `unzip`. Для новой Ubuntu с пакетным менеджером выполните

   ```sh
   sudo apt install openjdk-17-jdk-headless haproxy python3 openssl curl unzip
   ```

3. Для нового стенда скопируйте `deploy/env.example` в `deploy/local.env`. Укажите действительный `JAVA_HOME` и путь к HAProxy. На уже настроенном стенде используйте существующий `local.env`. Следующая команда устанавливает Jetty, WildFly и Consul в пользовательский каталог `SOA_RUNTIME`. Архивы проверяются по SHA-256. При отсутствии интернета их можно заранее положить в `$SOA_RUNTIME/downloads` под оригинальными именами.

   ```sh
   sh deploy/lab3/install.sh
   ```

4. Запустите стенд.

   ```sh
   sh deploy/lab3/run.sh
   ```

   Сценарий создаёт сертификаты, развёртывает EJB и WAR, ожидает статуса `passing` в Consul и готовности обоих агентств, затем выводит `Ready`. Повторный запуск использует существующие ключи. Для остановки нажмите `Ctrl+C` или выполните `sh deploy/lab3/run.sh stop` из второго терминала. Для обновления остановите стенд, выполните `mvn clean verify` и запустите его снова. Журналы каждого процесса находятся в `$SOA_RUNTIME/logs/`.

5. Откройте [API квартир](https://localhost:8443/flats) и [агентство](https://localhost:8444/agency/get-cheapest/1/2), разрешите браузеру использовать оба локальных сертификата. Для пустой коллекции агентство вернёт JSON с ошибкой `404`. Затем откройте [клиент](https://localhost:8443/app/index.html) или [Swagger UI](https://localhost:8443/docs/index.html).

## Проверка API

В отдельном терминале Linux/WSL загрузите параметры стенда и запустите сквозную проверку.

```sh
set -a
. deploy/local.env
set +a
python3 deploy/lab3/verify.py
```

Сценарий создаёт три квартиры и удаляет их по окончании. Он обращается к публичным HTTPS-портам, проверяет CRUD, два одновременных PATCH, фильтр, сортировку, статистику, ошибки валидации, сравнение цен и абсолютный `Location` при `303`. При повторных запросах к агентству должны ответить оба экземпляра. Сценарий проверяет сертификаты по файлам стенда.

## Демонстрация микросервисной архитектуры

**Регистрация и обнаружение.** В [Consul UI](http://localhost:8500/ui/) откройте `flats`. Проверка раз в 5 секунд вызывает `GET /flats?size=1` по HTTPS и проходит через EJB. Агентство запрашивает у Consul только экземпляры со статусом `passing`. Адрес для перенаправления браузера задаётся отдельно через `FLATS_PUBLIC_BASE_URL`.

```sh
curl -s "http://127.0.0.1:$CONSUL_HTTP_PORT/v1/health/service/flats?passing=true"
```

**Удалённый вызов и пул.** Jetty делает JNDI lookup `flats-ejb/FlatBusinessBean!ru.itmo.soa.contract.FlatOperations` в отдельной JVM WildFly. `EJB_POOL_MAX` в `deploy/local.env` задаёт предел числа одновременно используемых компонентов. WildFly создаёт экземпляры при конкурирующих вызовах. У каждого экземпляра один и тот же Singleton-репозиторий. При перезапуске WildFly коллекция очищается.

После загрузки `local.env` выполните запросы и прочитайте счётчики контейнера.

```sh
seq 1 60 | xargs -P 12 -I '{}' curl -fsS -o /dev/null \
  --cacert "$SOA_RUNTIME/jetty-base/etc/flats.crt" "https://localhost:$FLATS_HTTPS_PORT/flats"
sh "$WILDFLY_HOME/bin/jboss-cli.sh" --connect \
  --controller="127.0.0.1:$((9990 + WILDFLY_PORT_OFFSET))" \
  --command='/deployment=flats-ejb.jar/subsystem=ejb3/stateless-session-bean=FlatBusinessBean:read-resource(include-runtime=true)'
```

Поля `business-remote`, `pool-current-size`, `pool-max-size` и `peak-concurrent-invocations` показывают удалённый интерфейс, фактический размер пула, установленный предел и пик одновременных вызовов.

**Балансировка.** Порт 8444 принадлежит HAProxy. Для каждого ответа прокси добавляет имя выбранного сервера. Запрос с одинаковыми ID работает даже при пустой коллекции.

```sh
for i in 1 2 3 4; do
  curl -sS -D - -o /dev/null --cacert "$SOA_RUNTIME/agency-tls/agency.crt" \
    "https://localhost:$AGENCY_HTTPS_PORT/agency/get-cheapest/1/1" | grep -i x-agency-instance
done
```

Для демонстрации отказа завершите один процесс `agency-1` или `agency-2` командой `kill -TERM PID`, взяв его PID из вывода текущего запуска. Через несколько секунд HAProxy будет направлять все запросы на второй. Остановите и снова запустите стенд, чтобы вернуть оба экземпляра.
