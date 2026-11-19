# Core 1.0.1 — performance release

Собрано: JDK Temurin 25.0.5 + Maven 3.9.9, `mvn clean package` — BUILD SUCCESS.

## Оптимизации
- NameTagModule: ping/health считаются один раз на игрока за тик (было O(N^2)).
- ProtocolTrafficOptimizer: быстрый префильтр CORE_* по строковым полям пакета перед дорогим packet.toString().
- ItemCleanerModule: world.getEntitiesByClass(Item.class) вместо перебора всех сущностей.
- PlayerBoard: статический кэш имён команд/entry для 15 строк sidebar.
- ScoreboardModule: настройка скрытия цифр читается один раз за обновление, а не на каждого игрока.
- EntityLimiterModule: лимиты по мирам кэшируются (сброс при start/stop/reload), списки-буферы переиспользуются, компаратор статический.
- BossBarModule: определения баров (цвет, стиль, прогресс, заголовок) парсятся один раз при старте/reload, а не на каждого игрока каждый тик; удаление устаревших баров без лишних копий.

## Исправления
- Core.reloadAll: проверка messenger на null (раньше возможен NPE при /core reload).
- plugin.yml: добавлены description для всех команд.
- pom.xml: версия 1.0.1.

## Не тестировалось
- Плагин собран, но не запускался на сервере Paper 26.2.
