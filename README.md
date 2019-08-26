# Driver
Драйвер для загрузки данных с объектов

Написаны драйверы:
1. МСТ-20
2. ИАСДТУ
3. SA94

ИАСДТУ использует DataSource для которого надо установить fetchSize = 10000 и Emulate Two-Phase Commit

Для самого драйвера требуется 2 DataSource:
1. DataSource для которого надо установить Emulate Two-Phase Commit
2. DataSource для которого надо отключить Wrap Data Types

Настройки программы:
1. В web.xml поменять путь к фаловому хранилищу (моек: //172.16.4.47/c$/inetpub/ftproot; текон песочница: /mnt/datafile) 
можно через plan.xml
2. В AppConfigSBean настроить те сервера которые слушать
