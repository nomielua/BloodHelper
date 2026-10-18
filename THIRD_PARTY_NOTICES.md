# Происхождение материалов BLOODHELPER

Этот файл фиксирует использованные источники и сведения о лицензиях, доступные
при подготовке проекта 12 октября 2026 года. Новые классы BLOODHELPER находятся
в пакете `ru.bloodhelper.client`. Minecraft, Fabric API и Fabric Loader
используются как внешние зависимости и не включаются целиком в JAR клиента.

## Changes for Cooldown

- Проект: [Changes for Cooldown](https://modrinth.com/mod/changes-for-cooldown).
- Исследованная версия: **1.0.2 для Minecraft 1.21.11**,
  [версия Modrinth Quyq5rfb](https://modrinth.com/mod/changes-for-cooldown/version/Quyq5rfb).
- Автор в исходном `fabric.mod.json`: **Yz1kk**.
- Пользователь сообщил о разрешении владельца на перенос функций.

В BLOODHELPER адаптированы отображение активных кулдаунов предметов и соглашение
о выделенной группе `minecraft:trident` для PvP-таймера. Реализация перенесена
на собственную конфигурацию и общий интерфейс, добавлены рабочие переключатели,
масштаб, прогресс, обработка неизвестных групп и редактор HUD. Исходный мод,
его AutoConfig/Cloth Config и интеграция Mod Menu в JAR BLOODHELPER не упакованы.

В источнике есть несовпадение обозначений лицензии: страница Modrinth и
`fabric.mod.json` указывают **MIT**, а вложенный файл
`LICENSE_changes-for-cooldown` содержит **CC0 1.0 Universal**. Оба исходных
свидетельства сохранены без исправления:

- [Оригинальный текст вложенной лицензии](docs/licenses/LICENSE_changes-for-cooldown).
- [Оригинальный fabric.mod.json версии 1.0.2](docs/licenses/changes-for-cooldown-1.0.2-fabric.mod.json).

Эта запись не заменяет противоречащие друг другу сведения новой лицензией и не
присваивает авторство исходной функции разработчику BLOODHELPER.

## Punch ClickGUI

Источник — предоставленный пользователем `Punch_ClickGUI_Package.rar`, Java-пакеты
`org.blade`. Ранее подготовленный `Punch_ClickGUI_Clean` использован как образец
интерфейса. В полученном наборе исходников отдельный файл лицензии и полное
описание авторства отсутствуют.

Punch использовался как визуальный референс ранних вариантов `BloodHelperScreen`
и `UiDraw`. В версии 1.0.4 основная компоновка меню заменена адаптацией Expensive,
описанной ниже. Внутренние зависимости рендера Punch, его менеджер функций
и игровые модули в клиент не включены.

## Expensive 4.0: GUI и файлы OTF

Источник: опубликованный репозиторий
[dest4590/Expensive_4.0](https://github.com/dest4590/Expensive_4.0), коммит
`3cf31b7ea09d28312e9743b2f5dd71498ba682a5`. В его `gradle.properties` указана
Minecraft **1.20.1**. В BLOODHELPER GUI адаптирован к **1.21.11**; версия донора
не выдаётся за 1.21.11. Связь публикации с предоставленной темой
[YouGame 387054](https://yougame.biz/threads/387054/) не подтверждена.

За основу взяты компоновка `MenuScreen`, боковые категории, две колонки
карточек, заголовки, чекбоксы и настройки внутри карточек. Фон `background.png`
и вкладка `tab.png` перекрашены с сохранением геометрии, узора и прозрачности.
У иконок ico/render/palette/settings RGB заменён белой маской для окрашивания
темой; размеры и альфа-канал сохранены. Check, search и steve перенесены побайтно.
Список файлов, исходные пути, коммит и SHA-256 находятся в
`assets/bloodhelper/textures/expensive/provenance.json`.

Побайтово перенесены два шрифтовых файла из
`src/main/resources/assets/minecraft/expensive/fonts/`:

| Файл | Встроенное имя | SHA-256 |
| --- | --- | --- |
| `sfpromedium.otf` | SF Pro Display Medium | `cbfd17919856efcedabdd73781dad2984326928ae7c9635bef6227b1a7d71d21` |
| `sfprosemibold.otf` | SF Pro Display Semibold | `53a55ec7e0a90c0e1980b9d95e3cb93851b4e473b575693813a1b150066661ff` |

В проекте файлы находятся в `assets/bloodhelper/fonts/`; дополнительная запись
о них — `CustomFont-provenance.txt`. Собственный `CustomFont` загружает OTF через
AWT, получает метрики и растеризует глифы в свои атласы. Донорские классы рендера
не используются. Код игровых функций, управление движением, сетевые обработчики,
аккаунты, авторизация, загрузчик и сборочные зависимости донора не перенесены.
Архив донора исследован как данные; чужой клиент не собирался и не запускался.

Исходный `LICENSE` содержит `Copyright (c) 2024 salam4ik, dedinside, ****` и
`All rights reserved.` Он сохранён без изменений в
[docs/licenses/LICENSE_Expensive_4.0](docs/licenses/LICENSE_Expensive_4.0).
Встроенные сведения об авторских правах шрифтов также сохранены. Публикация
репозитория и этот перечень происхождения не заменяют условия правообладателей
новой лицензией. Оформление и шрифты не объявляются оригинальными работами
BLOODHELPER. Подробности адаптации:
[docs/EXPENSIVE_GUI_RU.md](docs/EXPENSIVE_GUI_RU.md).

## KineticLyrics / Wyvern

Источник — предоставленный пользователем `zzz.rar`, семь файлов с пакетами
`wtf.wyvern`, включая `KineticLyrics`, `LyricParticle3D`, LRC-парсер и LRCLIB-клиент.
Отдельного текста лицензии или полного описания авторства в полученном архиве
нет.

В BLOODHELPER адаптирована идея синхронизированных строк в трёхмерном мире,
закреплённых в месте появления, и режимы анимации. Реализация использует Fabric
1.21.11, собственный LRC-сервис, рендер Minecraft и мост Windows GSMTC. Wyvern,
его шрифты, система модулей и библиотека `mediatransport4j` не упакованы в клиент.

## Изображения Bloodstone

Источник: [Trard/bloodstone3](https://github.com/Trard/bloodstone3), коммит
`782ea4fc458a7f3d4c9738629d2d2ad3ff59676f`.

- `pack.png` — исходная пиксельная иконка сердца.
- `assets/logo/textures/bloodstone.png` и части
  `assets/logo/textures/bloodstone_big/logo_1.png`–`logo_4.png` — исходный логотип.
- Предыдущая составная версия BLOOD/сердце/HELPER сохранена в `branding/legacy`.
- В версии 1.0.2 логотип меню заменён изображением, присланным пользователем
  12 октября 2026 (`branding/source/user-logo-2026-10-12.png`). Убраны только
  пустые поля и внешняя подложка; буквы и цвета не перерисованы.

Исходные изображения и сведения о происхождении сохранены в `branding/source/`,
размеры и SHA-256 — в [branding/manifest.json](branding/manifest.json).
[Описание генерации](branding/README_RU.md) содержит прямые ссылки на каждый
исходный файл по зафиксированному коммиту. В полученных материалах отдельные
условия лицензии изображений не указаны; проект сохраняет исходное авторство
Bloodstone и не объявляет эти изображения своими оригинальными работами.

## Внешние компоненты и сервисы

| Компонент | Использование и источник |
| --- | --- |
| Fabric Loader | Внешний загрузчик модов. [Исходная лицензия Apache-2.0](https://github.com/FabricMC/fabric-loader/blob/master/LICENSE). |
| Fabric API | Внешняя зависимость событий, GUI, ввода и рендера. [Исходная лицензия Apache-2.0](https://github.com/FabricMC/fabric-api/blob/1.21.11/LICENSE). |
| Minecraft / официальные имена классов Mojang | Игра, API и имена для разработки; полные игровые файлы в JAR BLOODHELPER не распространяются. |
| LRCLIB | Внешний сервис синхронизированных текстов песен: [lrclib.net](https://lrclib.net). Тексты запрашиваются во время работы только при включённой функции; готовая база песен в клиент не включена. |
| Windows GSMTC | Системный API текущей медиасессии. [Документация Microsoft](https://learn.microsoft.com/en-us/uwp/api/windows.media.control.globalsystemmediatransportcontrolssessionmanager). Мост PowerShell написан для BLOODHELPER. |

Клиент не заявляет аффилированность с Mojang, Microsoft, Fabric, Punch, Expensive, Delta, Wyvern,
LRCLIB или авторами исходного мода и ресурспака.

## Ранее исследованный Delta 1.21.11 и оформление HUD

Источник: [Kuriom/delta1.21.11](https://github.com/Kuriom/delta1.21.11),
коммит `5f7b662ca903ef24585bf06b8d325c39b47dea0e`.
В `gradle.properties` указана версия Minecraft 1.21.11; в `fabric.mod.json` —
Delta 3.0.14. Происхождение самой публикации независимо не удостоверено.
Переданная пользователем тема [YouGame 387054](https://yougame.biz/threads/387054/)
возвращала HTTP 429, поэтому её связь с этим репозиторием не подтверждена.

В предыдущих версиях были перенесены четыре шрифтовых ресурса:

- `assets/delta/fonts/onest_regular.json` и `onest_regular.png` — Onest,
  используемый исходным `GUIPanel` в меню;
- `assets/delta/fonts/gt_regular.json` и `gt_regular.png` — атлас HUD,
  используемый исходными `Widget` и `CooldownsWidget`. Название `gt_regular`
  сохранено: семейство и правообладатель по нему не выдумываются.

Это MSDF/MTSDF-атласы с исходными метриками. В 1.0.4 они, прежний адаптер
к Minecraft `Font` и связанные текстовые шейдеры удалены. Текущий выпуск
не упаковывает Onest/`gt_regular`: меню, поля ввода и HUD используют
OTF-рендерер `CustomFont`, описанный выше.

По визуальному коду HUD адаптированы компактные отдельные шапки/строки,
расположение иконок, разделители и выравнивание времени. Система модулей, игровые функции,
сетевые обработчики, авторизация и загрузчик Delta не включены. Целый чужой
клиент не собирался и не запускался.

Отдельной лицензии шрифтов в просмотренном исходном проекте нет.
Поле `license` в его `fabric.mod.json` содержит `Minecraft EULA`; это исходное
обозначение не используется как новая лицензия на сторонние шрифты.
Атласы и чужое оформление не объявляются оригинальными работами BLOODHELPER.

## Inter и 3D-тексты

Существующий шрифт на основе [Inter 4.1](https://github.com/rsms/inter/releases/tag/v4.1),
Copyright 2016 The Inter Project Authors, сохранён для текстов песен в мире.
3D-рендерер продолжает использовать Minecraft `Font` через `UiFonts.world()`;
меню и HUD работают через `CustomFont` с OTF. Новому OTF-рендереру Inter
не служит скрытым запасным шрифтом. Исходный variable TTF загружен из
[Google Fonts](https://github.com/google/fonts/tree/main/ofl/inter); SHA-256
сохранён в `branding/fonts/manifest.json`. Статические производные начертания
550/650 с optical size 14 переименованы в BloodHelper UI. Полный текст SIL
Open Font License сохранён в `docs/licenses/OFL-Inter.txt` и включён в JAR.

## Иконки интерфейса 1.0.3

Геометрия 24 значков и генератор SDF-атласа `branding/build_ui_icons.py`
написаны для BLOODHELPER. Этот набор сохраняется для HUD, редактора и части
дополнительных элементов меню; импортированные PNG Expensive перечислены
отдельно выше. Набор не является шрифтовым или игровым кодом Delta.
Сердце в иконке самого приложения
по-прежнему происходит из указанных выше ресурсов Bloodstone.
