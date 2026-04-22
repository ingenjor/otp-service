# OTP Service

Backend-сервис для защиты операций с помощью одноразовых кодов (OTP). Поддерживает JWT-аутентификацию, многоканальную рассылку и настройку параметров кодов.

## Возможности

- Регистрация и вход (роли USER и ADMIN)
- JWT-токены с ограниченным сроком действия
- Генерация OTP с привязкой к идентификатору операции
- Отправка кода через:
    - Email (SMTP)
    - SMS (эмулятор SMPP, например SMPPsim)
    - Telegram бот (персонализированная отправка на сохранённый chat_id)
    - Сохранение в файл `otp_codes.txt` в корне проекта
- Валидация OTP
- Администратор может изменять длину и TTL кода, просматривать и удалять обычных пользователей
- Фоновое обновление статусов просроченных кодов (EXPIRED)
- Подробное логирование (SLF4J + Logback)

## Требования

- Java 17+
- Maven 3.6+
- PostgreSQL 17
- (Опционально) SMPPsim для тестирования SMS
- (Опционально) Telegram бот для тестирования отправки в Telegram

## Настройка

1. Создайте базу данных PostgreSQL и выполните скрипт `init.sql` из корня проекта.
2. Отредактируйте конфигурационные файлы в `src/main/resources/`:
    - `application.properties` – настройки БД (URL, пользователь, пароль) и JWT (секрет, время жизни).
    - `email.properties` – параметры SMTP-сервера.
    - `sms.properties` – подключение к эмулятору SMPP.
    - `telegram.properties` – токен бота (получить у [@BotFather](https://t.me/BotFather)).
3. Для работы Telegram-канала пользователь должен после регистрации вызвать `POST /api/user/telegram` с параметром `chatId`, который можно узнать, отправив боту сообщение и выполнив `getUpdates`.

## Сборка и запуск

```bash
mvn clean package
java -jar target/otp-service-1.0-SNAPSHOT.jar
Сервер запускается на порту, указанном в application.properties (по умолчанию 8080).

API Эндпоинты
Публичные
POST /api/register
json
{
  "login": "user1",
  "password": "pass",
  "role": "USER"
}
Ответ: 201 Created с данными пользователя.

POST /api/login
json
{
  "login": "user1",
  "password": "pass"
}
Ответ: 200 OK с телом { "token": "..." }.

Пользовательские (требуют заголовок Authorization: Bearer <token>)
POST /api/user/telegram
Привязка Telegram chatId к учётной записи.

json
{
  "chatId": "123456789"
}
POST /api/otp/generate
json
{
  "operationId": "op123",
  "channel": "email",          // email, sms, telegram, file
  "destination": "user@example.com"  // для email/sms/file; для telegram не требуется
}
Ответ: 200 OK с {"status":"success","operationId":"op123"}.

POST /api/otp/validate
json
{
  "operationId": "op123",
  "code": "123456"
}
Ответ: 200 OK при успехе, 400 при неверном или истёкшем коде.

Администраторские (только роль ADMIN)
GET /api/admin/config
Возвращает текущую конфигурацию OTP.

PUT /api/admin/config
json
{
  "codeLength": 6,
  "ttlSeconds": 300
}
Обновляет длину и время жизни кода.

GET /api/admin/users
Возвращает список всех пользователей с ролью USER.

DELETE /api/admin/users/{id}
Удаляет пользователя и все его OTP-коды.
```


Логирование
Логи записываются в консоль и в файл logs/otp-service.log с ежедневной ротацией.

Тестирование
SMS: запустите SMPPsim (или другой эмулятор SMPP), сообщения будут выводиться в его консоли.

Telegram: создайте бота через @BotFather, отправьте ему любое сообщение, получите chat_id через вызов getUpdates, затем привяжите его к своему аккаунту через API.

Email: используйте реальный SMTP-сервер или эмулятор (например, Mailhog).

Для быстрого тестирования через PowerShell можно использовать примеры запросов, приведённые выше.
