# Sentry: Java и нативные JNI-креши

Все команды выполняются из папки `sentry`. Это самостоятельный пример на основе лабораторной из `../lab1`.

## Sentry и Gradle

### Конфигурация проекта

Используем бесплатный Developer-план и Error Monitoring. Java SDK — `io.sentry:sentry:8.61.1`, Sentry Native — `0.17.4`, Gradle Wrapper создан с Gradle `9.6.1`.

`build.gradle` подключает плагин `application`, зависимость SDK из Maven Central и Java toolchain 21. Java-файлы оставлены в корне `sentry`: `sourceSets` включает только `*.java` из этой папки. Главный класс — `Main`. Задача `run` задаёт `java.library.path` абсолютным путём к `sentry`.

Gradle пока собирает только Java: C-библиотеку необходимо собирать отдельно. Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper`) сохраняем в Git; `.gradle/` и `build/` игнорируем. `./gradlew clean` удалит и SDK из `build/native-deps`, и локальную базу отчётов, поэтому нужные неотправленные дампы предварительно сохраняем вне `build`.

Переменные окружения:

```sh
export SENTRY_DSN='<DSN из настроек проекта>'
export SENTRY_AUTH_TOKEN='<токен для загрузки символов>'
```

В опыте переменные заданы в `~/.zshenv`. Для уже открытого терминала изменения применяются через `source ~/.zshenv`. Реальные значения не записываем в репозиторий. DSN определяет получателя событий; auth token для их отправки не нужен, но используется CLI для загрузки символов. Java-агент и Sentry Gradle Plugin здесь не подключены; Java source context автоматически не загружается.

### Java-исключения

В начале `main` устанавливается обработчик печати стека, затем вызывается `Sentry.init` с DSN из окружения, environment `local` и release `jni-lab1@1.0`. Предварительно установленный обработчик позволяет видеть стек в терминале после обработки исключения SDK.

Устаревший `options.setSendDefaultPii(false)` удалён: в SDK этой версии настройка по умолчанию выключена. Для вывода предупреждений компилятора включён `-Xlint:deprecation`.

| Режим | Сценарий | Отправка |
|---|---|---|
| `sentry` | Тестовый IllegalStateException | Вручную через captureException |
| `sentry-jni` | C устанавливает NullPointerException для stringLength(null) | Java catch и captureException |
| `sentry-unhandled` | То же JNI-исключение без catch | Обработчик неперехваченных исключений SDK |

```sh
./gradlew run --args="sentry"
./gradlew run --args="sentry-jni"
./gradlew run --args="sentry-unhandled"
```

В ручных сценариях `Sentry.close()` даёт SDK возможность отправить очередь перед завершением короткой программы, в пределах настроенного тайм-аута. Выданный event ID сам по себе не доказывает доставку — её проверяем в интерфейсе Sentry.

В `sentry-jni` ожидается сообщение `text must not be null` и стек с `NativeLab.stringLength(Native Method)` и `Main.main`. Это Java-стек: строки C из него не восстанавливаются. Событие отправлено как обработанное.

В `sentry-unhandled` исключение автоматически отправляется как необработанное (`handled: false`), JVM завершается с кодом 1, а Gradle выводит `Task :run FAILED`. Для этого опыта такое завершение ожидаемо. События могут объединяться в одну Issue; проверяем последнее событие внутри неё. Все три сценария проверены пользователем в Sentry.

### Сборка Sentry Native

Потребуются Git, CMake и Apple Clang. Из `lab1`:

```sh
mkdir -p build/native-deps

git clone --branch 0.17.4 --depth 1 --recurse-submodules --shallow-submodules \
  https://github.com/getsentry/sentry-native.git \
  build/native-deps/sentry-native

export SDKROOT="$(xcrun --sdk macosx --show-sdk-path)"

cmake --fresh -S build/native-deps/sentry-native \
  -B build/native-deps/sentry-build \
  -DCMAKE_BUILD_TYPE=RelWithDebInfo \
  -DCMAKE_OSX_ARCHITECTURES=arm64 \
  -DSENTRY_BACKEND=crashpad \
  -DSENTRY_BUILD_SHARED_LIBS=ON

cmake --build build/native-deps/sentry-build --parallel 4
cmake --install build/native-deps/sentry-build \
  --prefix "$PWD/build/native-deps/sentry-install"
```

При повторной сборке существующего клона шаг `git clone` пропускаем. Установлены `include/sentry.h`, `lib/libsentry.dylib` и `bin/crashpad_handler`. Проверена архитектура ARM64. Crashpad — отдельный процесс для захвата нативного отчёта.

### Актуальная сборка JNI-библиотеки

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
javac -h . NativeLab.java

clang -std=c17 -c \
  -g -O0 -fno-omit-frame-pointer -mno-omit-leaf-frame-pointer \
  -Wall -Wextra \
  -I"$JAVA_HOME/include" \
  -I"$JAVA_HOME/include/darwin" \
  -I"$PWD/build/native-deps/sentry-install/include" \
  NativeLab.c -o NativeLab.o

clang -dynamiclib NativeLab.o \
  -L"$PWD/build/native-deps/sentry-install/lib" \
  -lsentry \
  -Wl,-rpath,@loader_path/build/native-deps/sentry-install/lib \
  -o libnativelab.dylib

dsymutil libnativelab.dylib
./gradlew classes
```

`-lsentry` подключает SDK; `rpath` позволяет найти его dylib относительно JNI-библиотеки. Если `libnativelab.dylib` отсутствует, Gradle сам её не создаст: возникнет `UnsatisfiedLinkError`. После подключения `sentry.h` старой команды сборки без include/link параметров Sentry недостаточно.

### Нативный креш и Crashpad

```sh
export SENTRY_NATIVE_HANDLER="$PWD/build/native-deps/sentry-install/bin/crashpad_handler"
export SENTRY_NATIVE_DATABASE="$PWD/build/sentry-native-db"

./gradlew run --args="sentry-native"
```

`initNativeSentry()` читает эти пути и DSN, задаёт release/environment, включает debug-вывод и вызывает `sentry_init`. Код 0 означает успешную инициализацию. Путь обработчика задан явно: иначе поиск шёл бы рядом с исполняемым файлом `java`. `sentry_init` принимает владение объектом настроек. Только после успешной инициализации вызывается наша цепочка с SIGSEGV.

В опыте захват сработал: в `build/sentry-native-db/pending` сохранился minidump размером около 674 КБ. Автоматическая отправка Crashpad завершилась примерно через минуту с `NSURLErrorDomain -1001` (тайм-аут). Gradle сообщил код завершения 134. Также напечатано сообщение об отсутствующем `__sentry-attachments`; оно не помешало созданию minidump.

Использовался VPN. Проверка через `curl -I` подтвердила доступность ingest-сервера (TLS и HTTP 404), а последующая ручная отправка дампа прошла успешно. Это не доказывает, что тайм-аут Crashpad вызван именно VPN. Автоматическая доставка пока не исправлена.

### Ручная отправка сохранённого minidump

Подставляем имя нужного `.dmp` и minidump URL проекта из настроек Sentry или debug-лога SDK. URL имеет вид `https://<ingest-host>/api/<project-id>/minidump/?sentry_key=<public-key>`.

```sh
curl --connect-timeout 10 --max-time 90 \
  --fail-with-body \
  -w '\nHTTP %{http_code}\n' \
  -F 'upload_file_minidump=@build/sentry-native-db/pending/<report-id>.dmp' \
  -F 'sentry={"release":"jni-lab1@1.0","environment":"local"}' \
  '<minidump URL проекта>'
```

Получен HTTP 200 и ID `12f07b93-2061-4904-9a5b-9cf50c17a26b`. Событие найдено в Sentry. Фильтр времени должен включать момент исходного креша. Этот путь отправляет дамп вручную и не исправляет транспорт Crashpad.

### Загрузка символов

Установка CLI через Homebrew остановилась из-за требования более нового Xcode. Вместо неё использован готовый CLI через npm:

```sh
npx --yes @sentry/cli@3.8.2 debug-files upload \
  --org q1zin \
  --project java \
  libnativelab.dylib.dSYM
```

Команда использует `SENTRY_AUTH_TOKEN` из окружения. Успешно загружен ARM64 debug companion с ID `ffa2b873-85d1-3ec2-8322-361b19c0a2db`. После загрузки символов пользователь подтвердил корректную расшифровку события. Если старое событие обработано без символов, сохранённый дамп можно отправить повторно после их загрузки.

Не пересобираем библиотеку между получением дампа и сохранением соответствующего dSYM. Связь символов с бинарником определяется идентификатором сборки, а не одним только release. Повторная отправка дампа может создавать дополнительные события.

**Результат:** Java-исключения отправляются вручную и автоматически; нативный креш захватывается Crashpad, вручную доставляется в Sentry и расшифровывается через соответствующий dSYM. Автоматическая доставка нативного отчёта остаётся нерешённой.

## Документация

- [Sentry Java SDK](https://github.com/getsentry/sentry-java)
- [Sentry Native](https://github.com/getsentry/sentry-native)
- [Sentry CLI](https://www.npmjs.com/package/@sentry/cli)
