# Calendar 2.0

Android-календарь поверх системных календарей телефона (Google, Exchange, Yandex, локальные и др.). Без своего облака, рекламы и аналитики.

- Сайт: [laktapp.ru](https://laktapp.ru/)
- Скачать: [RuStore](https://www.rustore.ru/catalog/app/ru.laktapp.calendar)
- Исходники: [github.com/TrTalkTech/calendar-android](https://github.com/TrTalkTech/calendar-android)
- Пакет: `ru.laktapp.calendar`
- Лицензия: [Apache License 2.0](LICENSE)

## Возможности

- Виды месяц / год / день, задачи, поиск
- Создание и правка встреч, повторы, участники, вложения
- Виджеты (дата 1×1, месяц)
- Карта и подсказки адреса через публичный API Яндекс.Карт (без ключа в репозитории)
- Тёмная тема

## Сборка

Требования: Android Studio (или JDK 11+) и Android SDK.

```bash
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Release-подпись локально:

1. Скопировать `keystore.properties.example` → `keystore.properties`
2. Указать путь к своему `.jks` и пароли (файл в git не попадает)
3. `./gradlew :app:assembleRelease`

## Обратная связь

support@laktapp.ru
