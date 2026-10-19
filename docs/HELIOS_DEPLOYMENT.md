# Подготовка компонентов к Helios

Актуальная пошаговая инструкция для **обоих API, клиента и Swagger**, со списком файлов, ZIP-пакетом, SSH/scp, туннелями, запуском и остановкой: **[HELIOS_RUNBOOK.md](HELIOS_RUNBOOK.md)**. Ниже сохранены технические детали отдельных контейнеров. Пользователь переносит и проверяет Helios самостоятельно.

Конфигурация подготовлена; размещение на Helios не выполнялось. ОС, JDK, квоты и назначенные порты пока не проверены.

1. На Helios выполнить `sh deploy/helios/preflight.sh`, проверить JDK 17+, архитектуру, разрешённый высокий порт, память/диск. Для SunOS/Solaris/illumos нужен JDK этой ОС, не Linux-бинарники. Уточнить доступ браузера напрямую или через SSH-туннель.
2. Локально выполнить `mvn clean verify`. Переносить flats-service/target/flats-service.war, flats-service/target/flats-service-jetty-errors.jar, deploy/ и распакованный Jetty **12.0.40**. Maven/Java-исходники на Helios не нужны. Сохранить относительные пути deployment-пакета для prepare.sh.
3. Собственные JETTY_HOME/JETTY_BASE — в личном каталоге. Ключи не добавлять в git; SAN задать для адресов браузера и будущего агентства.
4. Запустить prepare.sh/start.sh: root, Docker, systemd, curl и доступ к Maven Central на Helios не требуются.

Заменить значения по результатам проверки среды:

```sh
export JAVA_HOME=/path/to/compatible/jdk17
export JETTY_HOME=/path/to/jetty-home-12.0.40
export JETTY_BASE=/path/to/private/jetty-base
export FLATS_HTTPS_PORT=YOUR_ASSIGNED_PORT
export FLATS_BIND_ADDRESS=127.0.0.1
export FLATS_CERT_SAN=dns:localhost,ip:127.0.0.1,dns:ACTUAL_HELIOS_HOSTNAME
sh deploy/jetty/prepare.sh
sh deploy/jetty/start.sh
```

Для внешнего HTTPS выбрать разрешённый bind address и включить DNS/IP в SAN. Для SSH-туннеля оставить loopback, перенаправить TLS-порт и использовать https://localhost:LOCAL_PORT; TLS сохраняется на внутреннем переходе. Публичный сертификат передать клиенту для доверия. Агентство использует отдельный truststore.

После закрытия SSH: `nohup sh deploy/jetty/start.sh > PRIVATE_LOG_FILE 2>&1 &`. Сохранить PID своего процесса, проверить команду/владельца перед остановкой. Не применять pkill java. Это инструкция будущего deployment, а не заявление о запущенном Helios-процессе.

Проверить HTTPS с доверенным сертификатом, отрицательные TLS-сценарии, отсутствие HTTP connector, единственный listener процесса на выбранном TLS-порту, API/валидацию/414. Integration-профиль можно запускать локально через SSH-туннель. Изменить heap под квоту при необходимости.

Shell-скрипты с LF запускаются через sh; Bash/исполняемый бит не требуются. Проверка синтаксиса и реальный запуск на Helios — разные проверки. Совместимость подтверждается только на целевой ОС/JVM.

## Дополнение: Payara / агентство

Второй сервис подготовлен и проверен локально на настоящей Payara Server 6.2025.10 / JDK 17.0.8; см. docs/AGENCY_SERVICE.md. На Helios он пока не запускался. Payara требует полноценный совместимый JDK (для source launcher ConfigureDomain.java нужен модуль jdk.compiler), собственный доступный для записи домен и согласованные свободные порты. Если Helios использует ОС вне [официального списка платформ Payara](https://docs.payara.fish/community/docs/6.2025.10/General%20Info/Supported%20Platforms.html), включая Solaris, совместимость нельзя считать подтверждённой. Сначала проверить ОС/JDK/архитектуру/квоты; отсутствие подходящей JVM блокирует запуск обоих контейнеров.

Дистрибутив Payara можно скачать и проверить SHA-512 локально, затем перенести на Helios вместе с WAR, deploy/payara и deploy/tls. Ни root/sudo, ни Docker не требуются. Не переносить готовый Windows domain.xml: создать домен на целевой системе. Каталоги runtime/домена держать вне git, с umask 077. Heap по умолчанию Payara 512m плюс Jetty 256m и внеheap-память требуют проверки квоты; ConfigureDomain.java содержит единственную настройку Xmx Payara.

Пример переменных (подставить фактические согласованные значения):

```sh
export JAVA_HOME=/path/to/jdk17
export PAYARA_HOME=/path/to/payara6
export PAYARA_DOMAINS=/path/to/private/payara-domains
export AGENCY_DOMAIN=agency
export AGENCY_PORT_BASE=YOUR_ASSIGNED_PORT_BASE
export AGENCY_ADMIN_PORT=PORT_BASE_PLUS_48
export AGENCY_HTTPS_PORT=YOUR_ASSIGNED_HTTPS_PORT
export AGENCY_BIND_ADDRESS=127.0.0.1
export AGENCY_CERT_SAN=dns:localhost,ip:127.0.0.1,dns:ACTUAL_HOSTNAME
export FLATS_CERTIFICATE=/path/to/jetty-base/etc/flats.crt
export FLATS_INTERNAL_URL=https://localhost:JETTY_PORT
export FLATS_PUBLIC_URL=https://localhost:CLIENT_TUNNEL_PORT
export AGENCY_ALLOWED_ORIGIN=https://localhost:CLIENT_ORIGIN_PORT
sh deploy/payara/prepare.sh
sh deploy/payara/manage.sh start
sh deploy/payara/manage.sh deploy
```

PortBase задаёт также служебные порты JMS/IIOP/JMX/Hazelcast; значения каждого должны быть разрешены и свободны. Admin HTTP listener ограничен loopback и отдельным virtual server, приложение на нём не развёртывается. HTTP listener приложения отключён. Для внешнего доступа согласовать bind address/SAN; для туннелей — публичный URL первого сервиса в Location и точный CORS origin. На клиенте доверять обоим публичным сертификатам; отключение проверки TLS не требуется.

Остановка своего домена: `sh deploy/payara/manage.sh stop`. Подготовку повторять только при остановленном домене. После переноса выполнить совместные интеграционные тесты и проверку отказов через туннели. POSIX-скрипты подготовлены с LF, но в текущей Windows-среде не запускались; это не подтверждение успешного deployment на Helios.
