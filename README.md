# Service-oriented architecture

Лабораторная №2, вариант 74611. Java 17, Maven, Lombok.

- `flats-service` — управление коллекцией квартир: Spring MVC, Jetty.
- `agency-service` — сравнение квартир по цене: JAX-RS, Payara.
- `contract-model` — общие модели, JSON и валидация.
- `client` — браузерный клиент на HTML/CSS/JavaScript.
- `public` — две спецификации OpenAPI и Swagger UI.
- `deploy` — подготовка и запуск серверов с HTTPS.

Условия: [лабораторная №1](docs/ORIGINAL_TASK.md), [лабораторная №2](docs/task2.png).
Цена `price` добавлена к исходной модели для операций агентства: сумма в рублях с шагом 0.01.
По условию параметры, включая JSON `flat`, передаются в URL. Коллекция хранится в памяти.

## Сборка и запуск

Нужны JDK 17 и Maven 3.9+. Для тестов клиента — Node.js.

```sh
mvn clean verify
node --test client/test/*.test.js
```

[Локальный запуск двух сервисов](docs/RUNNING.md) · [Работа на Helios](docs/HELIOS.md).

После локального запуска:

- Клиент: https://localhost:8443/app/index.html
- Swagger UI: https://localhost:8443/docs/index.html

В Swagger UI можно переключаться между API коллекции и агентства.
