# Service-oriented architecture

OpenAPI-спецификация двух сервисов для работы с квартирами и статическая документация Swagger UI.

## Состав

- `docs/ORIGINAL_TASK.md` — исходное условие лабораторной работы.
- `public/flats.yaml` — OpenAPI-спецификация сервиса квартир.
- `public/agency.yaml` — OpenAPI-спецификация сервиса агентства.
- `public/index.html` — страница Swagger UI.

## Локальный запуск

```bash
python -m http.server 8000 --bind 127.0.0.1 --directory public
```

Документация будет доступна по адресу <http://127.0.0.1:8000/>.
Сервис выбирается в списке в верхней части Swagger UI.
