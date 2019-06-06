# Driver
Драйвер для загрузки данных с объектов

Написано 2 драйвера:
1. МСТ-20
2. ИАСДТУ

ИАСДТУ использует DataSource для которого надо установить fetchSize = 10000 и Emulate Two-Phase Commit

Для самого драйвера требуется 2 DataSource:
1. DataSource для которого надо установить Emulate Two-Phase Commit
2. DataSource для которого надо отключить Wrap Data Types
