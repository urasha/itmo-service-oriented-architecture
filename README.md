# Сервис-ориентированная архитектура

Лабораторная работа №3, вариант 74611. Клиент и два HTTPS API работают по спецификациям предыдущих лабораторных. [Условие ЛР3](docs/task3.png) · [Условие ЛР2](docs/task2.png) · [Спецификации OpenAPI](public/)

```text
Браузер ── HTTPS :8443 ── Jetty / Spring MVC ── remote EJB ── WildFly
                                                          FlatBusinessBean
                                                          InMemoryFlatRepository
Браузер ── HTTPS :8444 ── HAProxy ┬── HTTPS :8445 ── Spring Boot / Jersey
                                  └── HTTPS :8446 ── Spring Boot / Jersey

Каждый экземпляр агентства → Consul → адрес доступного Jetty → HTTPS /flats/{id}
```

| Модуль | Ответственность |
| --- | --- |
| `contract-model` | Модели квартир, JSON, правила валидации и удалённый бизнес-интерфейс `FlatOperations` |
| `flats-service` | Spring MVC WAR на Jetty. Контроллеры обслуживают контракт HTTP, `FlatService` делегирует операции удалённому EJB. При запуске приложение регистрирует HTTPS-адрес в Consul |
| `flats-ejb` | Отдельный EJB JAR на WildFly. Stateless-компонент выполняет операции коллекции. Все экземпляры пула используют один Singleton-репозиторий с контейнерными блокировками |
| `agency-service` | Исполняемый Spring Boot JAR с JAX-RS-ресурсами. Оба процесса находят сервис квартир через Consul. HAProxy распределяет запросы по round-robin и проверяет доступность каждого процесса |
| `client`, `public` | Браузерный клиент, Swagger UI и обе спецификации OpenAPI |

## Запуск

Соберите артефакты командой `mvn clean verify`. Для подготовленного стенда в WSL из PowerShell выполните `wsl -d Ubuntu -- sh deploy/lab3/run.sh`. После сообщения `Ready` откройте [клиент](https://localhost:8443/app/index.html), [Swagger UI](https://localhost:8443/docs/index.html) или [Consul](http://localhost:8500/ui/).

[Установка, запуск и проверка требований](docs/RUNNING.md) · [Перенос на Helios](docs/HELIOS.md)
