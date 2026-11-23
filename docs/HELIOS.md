# ЛР3 на Helios

Для третьей лабораторной используйте отдельные каталоги `~/soa-lab3` и `~/soa-lab3-runtime`. Клиент и Swagger поставляются внутри WAR. Два локальных проброса SSH дают браузеру доступ ко всему приложению.

## Перенос артефактов

Из PowerShell в корне репозитория выполните

```powershell
mvn clean verify
powershell.exe -ExecutionPolicy Bypass -File deploy/package.ps1
scp.exe -P 2222 .runtime/soa-lab3-helios.tar.gz s413022@helios.cs.ifmo.ru:~/
ssh.exe -p 2222 s413022@helios.cs.ifmo.ru
```

В своей учётной записи подготовьте отдельный каталог и конфигурацию.

```sh
umask 077
mkdir -p ~/soa-lab3
cd ~/soa-lab3
tar -xzf ~/soa-lab3-helios.tar.gz
cp deploy/env.example deploy/local.env
```

## Настройки

В `deploy/local.env` задайте `JAVA_HOME=/usr/local/openjdk17` либо путь к доступному JDK 17. Проверьте `python3`, `openssl`, `curl`, `unzip` и `haproxy -vv`. Для HAProxy нужна сборка FreeBSD с OpenSSL. Если программу предоставляет кафедра, укажите её полный путь в `HAPROXY_BIN`.

Если в системе нет HAProxy, его можно собрать в своей домашней директории. Для FreeBSD потребуются `gmake`, компилятор C и заголовки OpenSSL. Из `~/soa-lab3` выполните

```sh
mkdir -p ~/soa-lab3-runtime/src ~/soa-lab3-runtime/bin
cd ~/soa-lab3-runtime/src
curl -fLO https://www.haproxy.org/download/2.8/src/haproxy-2.8.19.tar.gz
tar -xzf haproxy-2.8.19.tar.gz
cd haproxy-2.8.19
gmake -j2 TARGET=freebsd USE_OPENSSL=1
install -m 700 haproxy ~/soa-lab3-runtime/bin/haproxy
cd ~/soa-lab3
```

Укажите `HAPROXY_BIN="$SOA_RUNTIME/bin/haproxy"` в `deploy/local.env`.

Согласуйте свободные порты для своей учётной записи.

| Компонент | Параметры |
| --- | --- |
| Jetty и HAProxy | `FLATS_HTTPS_PORT`, `AGENCY_HTTPS_PORT` |
| Два процесса агентства | `AGENCY_PORT_1`, `AGENCY_PORT_2` |
| WildFly | `WILDFLY_PORT_OFFSET`. В конфигурации `standalone.xml` стенд использует `8080 + offset`, `8443 + offset`, `9990 + offset` |
| Consul | `CONSUL_HTTP_PORT`, `CONSUL_DNS_PORT`, `CONSUL_RPC_PORT`, `CONSUL_LAN_PORT`, `CONSUL_WAN_PORT`. DNS и gossip используют также UDP |

Публичные адреса `FLATS_PUBLIC_BASE_URL=https://localhost:8443` и `AGENCY_PUBLIC_BASE_URL=https://localhost:8444` относятся к браузеру. Для клиента внутри WAR задайте обеим переменным `*_ALLOWED_ORIGIN` значение `https://localhost:8443`. Для отдельно опубликованного на `se.ifmo.ru` клиента укажите `https://se.ifmo.ru`.

```sh
sh deploy/lab3/install.sh
sh deploy/lab3/run.sh
```

Дождитесь `Ready`. Для фонового запуска используйте `nohup sh deploy/lab3/run.sh > ~/soa-lab3/stack.log 2>&1 < /dev/null &`. Команда `sh deploy/lab3/run.sh stop` останавливает этот стенд. Если доступ к внешним репозиториям ограничен, скопируйте ZIP-архивы Jetty, WildFly и Consul для **FreeBSD amd64** в `~/soa-lab3-runtime/downloads/` до запуска `install.sh`. Названия и источники указаны в этом сценарии.

## Доступ из браузера

В отдельном PowerShell укажите свои фактические серверные порты Jetty и HAProxy. Пример для портов 32032 и 32033:

```powershell
powershell.exe -ExecutionPolicy Bypass -File deploy/helios/tunnel.ps1 -FlatsRemotePort 32032 -AgencyRemotePort 32033
```

Пока SSH-туннель открыт, подтвердите сертификаты [Jetty](https://localhost:8443/flats) и [HAProxy](https://localhost:8444/agency/get-cheapest/1/2), затем откройте [клиент](https://localhost:8443/app/index.html). [Сквозная проверка и демонстрация пула, регистрации и балансировки](RUNNING.md)
