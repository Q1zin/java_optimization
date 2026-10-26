# Лабораторная работа 1: JNI на macOS ARM64

Среда эксперимента: JDK 21.0.8, Apple Clang, macOS ARM64. Все команды ниже выполняются из `lab1`.

После подключения Sentry Java собирается через Gradle, а JNI-библиотека дополнительно линкуется с Sentry Native. Команды `javac Main.java` и `java Main` в первых разделах описывают этап до подключения SDK. Актуальные команды сборки и запуска находятся в разделе «Sentry и Gradle» ниже.

## Что сделано

- `Bean.java`: восемь примитивных типов, `String`, ссылка на `Bean`, все восемь видов массивов примитивов, `String[]` и `Bean[]`. Поля приватные, есть публичный конструктор без аргументов, геттеры и сеттеры.
- `NativeLab.java`: объявления нативных методов и загрузка `nativelab`.
- `NativeLab.h`: заголовок, сгенерированный `javac -h`.
- `NativeLab.c`: выделение/освобождение 1 КиБ, цепочка функций с намеренным крешем, отказ крупных выделений и эксперимент с заполнением памяти.
- `Main.java`: обычный режим измерения памяти и режимы `crash`, `memory`, `pressure`.

Во второй части Bean передаётся в C для вызова Java-метода и изменения поля. Результаты всех экспериментов приведены ниже.

## Как разрабатывается JNI-приложение

1. Объявляем Java-методы с `native`, без тела.
2. Компилируем Java и генерируем C-заголовок.
3. Реализуем объявленные функции в C, подключая заголовок.
4. Собираем динамическую библиотеку под нужную ОС и архитектуру.
5. Загружаем её через `System.loadLibrary` и вызываем методы из Java.

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
"$JAVA_HOME/bin/javac" -h . NativeLab.java
"$JAVA_HOME/bin/javac" Bean.java Main.java NativeLab.java
```

`-h .` записывает заголовок в текущую папку. Компиляция не выполняет статический блок загрузки библиотеки. Заголовок перегенерируем после изменения нативных объявлений; вручную его не редактируем.

`System.loadLibrary("nativelab")` на macOS загружает `libnativelab.dylib`. `-Djava.library.path=.` задаёт текущую рабочую папку для поиска. Для JNI на JDK 21 `--enable-native-access=ALL-UNNAMED` не требуется.

## Java-типы и JNI

| Java | JNI | Дескриптор |
|---|---|---|
| boolean | jboolean | Z |
| byte | jbyte | B |
| short | jshort | S |
| char | jchar | C |
| int | jint | I |
| long | jlong | J |
| float | jfloat | F |
| double | jdouble | D |
| String | jstring | Ljava/lang/String; |
| Bean без пакета | jobject | LBean; |
| int[] | jintArray | [I |
| String[] | jobjectArray | [Ljava/lang/String; |
| Bean[] | jobjectArray | [LBean; |

Для остальных массивов примитивов аналогично: `jbooleanArray`, `jbyteArray`, `jshortArray`, `jcharArray`, `jlongArray`, `jfloatArray`, `jdoubleArray`.

Java `char` — 16-битная кодовая единица UTF-16, а не C `char`. Java `long` всегда 64-битный, размер C `long` зависит от платформы.

`jobject` — управляемая JVM ссылка, а не указатель на C-структуру. Поля читаются через JNI с использованием `jclass`, `jfieldID` и, например, `GetIntField`. Локальные JNI-ссылки действуют в пределах соответствующего локального кадра; для длительного хранения нужны глобальные ссылки. `JNIEnv*` относится к текущему потоку.

Для статических методов первые аргументы C-функции — `JNIEnv*` и `jclass`. Для нестатического метода вместо `jclass` передаётся `jobject` — объект-получатель.

В заголовке `()J` означает «нет аргументов, возвращает long», `(J)V` — «принимает long, возвращает void». `JNIEXPORT` и `JNICALL` задают платформенные детали экспорта и вызова.

## Сборка с отладочной информацией

Сохраняем объектный файл, затем выполняем компоновку и собираем dSYM:

```sh
clang -std=c17 -c \
  -g -O0 -fno-omit-frame-pointer -mno-omit-leaf-frame-pointer \
  -Wall -Wextra \
  -I"$JAVA_HOME/include" \
  -I"$JAVA_HOME/include/darwin" \
  NativeLab.c -o NativeLab.o

clang -dynamiclib NativeLab.o -o libnativelab.dylib
dsymutil libnativelab.dylib
```

- `-c`: создать объектный файл без компоновки.
- `-g`: добавить отладочную информацию, включая связь инструкций со строками исходника.
- `-O0`: отключить оптимизацию для понятного учебного разбора.
- `-fno-omit-frame-pointer`: сохранять указатель кадра стека.
- `-mno-omit-leaf-frame-pointer`: сохранять его и у функций, которые не вызывают другие функции. Этот флаг добавлен после разбора пропавшего `levelTwo`; приведённый ниже исторический лог получен без него.
- `-Wall -Wextra`: дополнительные предупреждения.
- `include/darwin`: платформенные заголовки JNI для macOS.

При одношаговой сборке временный `.o` может быть удалён до отдельного вызова `dsymutil`. Тогда появляются `unable to open object file` и `no debug symbols`. Поэтому используем два этапа и сохраняем `.o`.

Сохраняйте библиотеку, соответствующий dSYM и версию исходников. Новая сборка не подходит для расшифровки старого креша только на основании совпадения имён файлов. UUID библиотеки и dSYM можно сравнить:

```sh
dwarfdump --uuid libnativelab.dylib
dwarfdump --uuid libnativelab.dylib.dSYM
```

## Эксперимент: 1024 байта в C и Runtime

`allocateKilobyte()` вызывает `malloc(1024)`, проверяет результат, заполняет блок через `memset` и возвращает адрес как `jlong` через `intptr_t`. Это учебный непрозрачный адрес: его нельзя произвольно менять или освобождать дважды. При неудаче выделения метод устанавливает `OutOfMemoryError`. `ThrowNew` не прерывает C-функцию: после него явно выполняется `return`.

`freeMemory()` вызывает `free`. GC не освобождает этот блок. В Java освобождение стоит в `finally`, который выполняется при обычном выходе и Java-исключениях, но не гарантируется при аварийном падении процесса.

```sh
java -Xms32m -Xmx32m -Djava.library.path=. Main
```

`-Xms32m` и `-Xmx32m` задают начальный и максимальный размеры Java heap: 32 МиБ.

Фактический результат одного запуска:

```text
Warmup: total=33554432, free=31413056, used=2141376, max=33554432 bytes
Before malloc: total=33554432, free=30741824, used=2812608, max=33554432 bytes
After malloc: total=33554432, free=30741824, used=2812608, max=33554432 bytes
After free: total=33554432, free=30741824, used=2812608, max=33554432 bytes
```

`totalMemory()` описывает текущий Java heap, `freeMemory()` — оценку свободного места в нём, разность — оценку занятого места, `maxMemory()` — максимальный объём heap, который JVM будет пытаться использовать. Это не вся память процесса.

**Вывод:** `malloc(1024)` выделяет память вне Java heap, поэтому эти методы не учитывают его напрямую. Неизменность значений сама по себе не доказывает успешность malloc/free: для этого нужны проверка результата и анализ выполнения C-кода.

Между `Warmup` и `Before malloc` оценка занятого heap выросла на 671232 байта. Замер внутри `printMemory` выполняется до `printf`, поэтому следующий замер уже может учитывать объекты форматирования, вывода и дополнительную инициализацию. По двум числам нельзя установить точные причины прироста; для этого нужен профилировщик. Один прогрев не гарантирует отсутствие дальнейших выделений и GC.

`free` возвращает блок C-аллокатору, но не обязательно сразу ОС. Общая память процесса также не обязана уменьшиться. Нативный адрес — виртуальный адрес внутри процесса, не физический адрес RAM.

## Эксперимент: креш в цепочке вызовов

Фактическая цепочка:

```text
Main.main → NativeLab.crashInCallChain
→ Java_NativeLab_crashInCallChain → levelOne → levelTwo → levelThree
```

В `levelThree` выполняется запись `*pointer = 42` при `pointer == NULL`. Это undefined behavior языка C; в нашем запуске оно привело к SIGSEGV. `volatile` не делает ошибочный адрес допустимым. C-код выполняется в процессе JVM и может завершить всё приложение; Java try/catch не превращает такой сбой в обычное исключение.

```sh
ulimit -c 0
java -XX:ErrorFile=./hs_err_pid%p.log -Djava.library.path=. Main crash
```

`ulimit` отключает core dump в этой сессии терминала. Текстовый fatal error log остаётся; `%p` заменяется PID. После добавления флага для leaf-функций выполнен повторный запуск: `levelTwo` появился в стеке.

В сохранённом `hs_err_pid37697.log`:

```text
SIGSEGV (0xb) at pc=0x00000001004b454c
C  [libnativelab.dylib+0x54c]  levelThree+0x10
si_signo: 11 (SIGSEGV), si_code: 2 (SEGV_ACCERR), si_addr: 0x0
```

`pc` — адрес упавшей инструкции; `si_addr` — адрес памяти, к которому она обращалась. `SEGV_ACCERR` означает нарушение прав доступа. `+0x54c` — смещение от начала библиотеки, `+0x10` — от начала функции, оба в байтах, а не строках.

База загрузки: `0x1004b454c - 0x54c = 0x1004b4000`. Для этой сохранённой сборки выполнено:

```sh
atos -arch arm64 \
  -o libnativelab.dylib.dSYM/Contents/Resources/DWARF/libnativelab.dylib \
  -l 0x1004b4000 \
  0x1004b454c
```

Результат:

```text
levelThree (in libnativelab.dylib) (NativeLab.c:44)
```

`-l` задаёт адрес загрузки библиотеки. Из-за ASLR он меняется между запусками. После пересборки и нового креша адреса и смещения необходимо брать из нового лога.

В стеке `C` — нативный код, `j` — интерпретируемый Java, `J` — JIT-компилированный Java, `V` — код JVM, `v` — сгенерированная служебная процедура. Стек читается от текущего места к вызывающим функциям. В Java-кадре `Main.main([Ljava/lang/String;)V+16` число 16 — индекс в байткоде, не строка исходника.

## Почему в Native frames отсутствует levelTwo

Проверены исходник, таблица символов, дизассемблированная библиотека и регистры из лога. Это не удаление levelTwo оптимизатором: функция существует и вызывается.

Команды для самостоятельной проверки:

```sh
nm -n libnativelab.dylib
otool -tvV libnativelab.dylib
```

В исходной сборке:

```text
levelOne:   0x51c bl levelTwo
levelTwo:   0x530 bl levelThree
            0x534 ... адрес возврата из levelThree
levelThree: 0x53c sub sp, sp, #0x10
            ...
            0x54c str w8, [x9]  — упавшая запись
```

ARM64 `bl` записывает адрес возврата в регистр `x30` (`lr`). В логе есть:

```text
lr=0x00000001004b4534: levelTwo+0xc
```

Это подтверждает вызов из levelTwo. При этом levelThree — leaf-функция (сама никого не вызывает): Clang не создал для неё собственную запись кадра с сохранением `x29` и `x30`, несмотря на `-fno-omit-frame-pointer`. В её машинном коде нет пары `stp x29, x30` и установки `x29`, которые есть у levelTwo.

В момент сбоя `fp` всё ещё указывает на запись кадра levelTwo. Сохранённый в этой записи адрес возврата ведёт уже в levelOne (`0x1004b4520`). Обход цепочки кадров в аварийном отчёте HotSpot поэтому показывает levelThree, затем levelOne: переход через живой `lr` в levelTwo в списке Native frames пропущен. Это ограничение восстановления стека, а не отсутствие вызова.

Для этой сборки добавляем `-mno-omit-leaf-frame-pointer`, сохраняющий запись кадра также у levelThree. После пересборки, dsymutil и нового запуска levelTwo появился в Native frames, что подтвердило объяснение. Отладочная информация и запись кадра — разные вещи: наличие dSYM само по себе не гарантирует полноту стека, напечатанного JVM.

## Эксперимент: отказ выделения виртуальной памяти

```sh
java -Xms32m -Xmx32m -Djava.library.path=. Main memory
```

`consumeMemory()` удерживает блоки по 1 ТиБ, не заполняя их. В нашем запуске успешно выделены 127 блоков; следующий запрос `malloc` вернул `NULL`:

```text
Allocated: 127 TiB of virtual memory
Exception in thread "main" java.lang.OutOfMemoryError: Native malloc failed to allocate another 1 TiB block
        at NativeLab.consumeMemory(Native Method)
        at Main.main(Main.java:19)
```

После отказа C-код освободил успешные выделения и явно установил Java `OutOfMemoryError` через JNI. Исключение не перехвачено, приложение завершилось. Это не авария JVM и не автоматическая реакция JVM на `malloc`; fatal error log здесь не ожидается. Номера строк относятся к версии исходников на момент запуска.

127 ТиБ — суммарный размер запросов, а не объём занятой RAM. Без заполнения таких блоков не требуется сразу предоставлять соответствующий объём физической памяти. Результат означает невозможность выделить ещё один блок размером 1 ТиБ в этом запуске, а не невозможность любого меньшего выделения и не точное измерение полного адресного пространства.

Попытка поставить лимит через `ulimit -v 262144` на этой macOS завершилась сообщением `setrlimit failed: invalid argument`; ограничение не было установлено.

## Эксперимент: заполнение памяти, сжатие и swap

```sh
java -Xms32m -Xmx32m -Djava.library.path=. Main pressure
```

`pressureMemory()` в цикле выделяет блоки с 1 МиБ данных, записывает каждый байт и удерживает блоки в связном списке. Поле `next` хранит ссылку на предыдущий блок; служебные поля и накладные расходы аллокатора не входят в печатаемый объём данных. Записи выполняются через `volatile`, чтобы компилятор их не устранил. Через каждые 64 блока выводится счётчик.

В проведённом опыте суммарный объём заполненных и удерживаемых данных превысил 80 ГиБ. За время наблюдения отказа `malloc` и завершения процесса ОС не произошло. Пользователь остановил эксперимент вручную. Поэтому этот запуск не демонстрирует падение из-за нехватки памяти.

macOS использует сжатие памяти и swap, поэтому превышение объёма физической RAM не обязательно сразу приводит к отказу выделения. Дополнительно записанный узор `(unsigned char)(i + count)` периодичен и хорошо поддаётся сжатию. Однако по одному счётчику выделенных блоков нельзя определить фактические объёмы RAM, сжатой памяти и swap или доказать причину отсутствия отказа. Рост swap следует подтверждать наблюдением в «Мониторинге системы», а не предполагать на основании 80 ГиБ.

**Вывод для защиты:** нативный метод выделил и заполнил более 80 ГиБ данных; за время наблюдения аллокатор не отказал, и ОС не завершила процесс. Эксперимент остановлен вручную. Сжатие и swap могут откладывать отказ, но не делают память бесконечной.

Отличие от Java: `-Xmx` ограничивает Java heap, но не эти C-выделения. При невозможности Java-выделения JVM может выбросить `OutOfMemoryError`; отказ C `malloc` возвращает `NULL` и сам по себе не создаёт Java-исключение. В `pressureMemory()` при таком отказе предусмотрены сообщение, освобождение блоков и обычный возврат. Если ОС принудительно завершит процесс, этот код очистки не выполнится; память процесса освободит ОС.

Режим `pressure` не имеет установленного жёсткого лимита нативной памяти и способен замедлить весь Mac. Для наблюдения используются процесс Java, график давления памяти и swap в «Мониторинге системы». Достигать отказа всей системы для повторения результата не требуется.

## Часть 2: строки, объекты и нативная структура

Все шесть методов реализованы; пользователь подтвердил корректную работу примеров. Ниже приведены ожидаемые результаты для текущих значений в `Main.java`.

### Подготовка и сборка

После добавления нативных объявлений перегенерируем заголовок, затем компилируем Java и C:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
javac -h . NativeLab.java
javac Bean.java Main.java NativeLab.java

clang -std=c17 -c \
  -g -O0 -fno-omit-frame-pointer -mno-omit-leaf-frame-pointer \
  -Wall -Wextra \
  -I"$JAVA_HOME/include" \
  -I"$JAVA_HOME/include/darwin" \
  NativeLab.c -o NativeLab.o

clang -dynamiclib NativeLab.o -o libnativelab.dylib
dsymutil libnativelab.dylib
```

В запусках используем `-Xcheck:jni` для дополнительных проверок обращений к JNI. Он не заменяет проверки ошибок и не гарантирует обнаружение ошибочных C-указателей, чтения после освобождения или двойного `free`.

### Получить String и вернуть длину

```java
public static native int stringLength(String text);
```

Дескриптор: `(Ljava/lang/String;)I`. C получает `jstring` и вызывает `GetStringLength`: результат совпадает с `String.length()` — числом кодовых единиц UTF-16. Для получения длины не нужно извлекать содержимое через `GetStringUTFChars` или затем освобождать его. `GetStringUTFLength` измеряет другую величину — число байтов JNI modified UTF-8.

```sh
java -Xcheck:jni -Djava.library.path=. Main strings
```

```text
"Hello": Java=5, JNI=5
"Привет": Java=6, JNI=6
"😀": Java=2, JNI=2
"": Java=0, JNI=0
null: java.lang.NullPointerException: text must not be null
```

Эмодзи занимает суррогатную пару — две кодовые единицы UTF-16, хотя это одна кодовая точка. Длина не является числом видимых символов. Для `null` явно устанавливается `NullPointerException`; пустая строка корректна и имеет длину 0.

### Получить объект и вызвать его метод

```java
public static native void callBeanMethod(Bean bean);
```

Дескриптор: `(LBean;)V`. Последовательность внутри C:

1. Проверить объект на `NULL`.
2. Через `GetObjectClass` получить класс объекта.
3. Через `GetMethodID` найти `getIntValue` с дескриптором `()I`.
4. Через `CallIntMethod` вызвать геттер у переданного объекта.
5. Проверить `ExceptionCheck`, затем напечатать результат.

`jmethodID` — идентификатор метода, не его результат. Это виртуальный вызов: учитывается переопределение метода в подклассе. Метод исполняется в JVM; C получает возвращённый `jint`. В текущем `Main.java` перед вызовом установлено значение 228.

```sh
java -Xcheck:jni -Djava.library.path=. Main call
```

```text
C called Bean.getIntValue(): 228
```

Если Java-метод выбросит исключение, C-код обнаружит его и вернётся, сохранив исключение для Java. Полученное при исключении числовое значение нельзя использовать как успешный результат. Локальная ссылка на класс очищается при выходе из нативного вызова; это не удаляет объект Bean. `jmethodID` не является локальной объектной ссылкой.

### Получить объект и изменить его поле

```java
public static native void changeBeanInt(Bean bean, int value);
```

Дескриптор: `(LBean;I)V`. После проверки `NULL` и получения класса `GetFieldID` находит поле `intValue` с дескриптором `I`, затем `SetIntField` записывает новое значение.

`I` описывает тип поля; `()I` описывает метод без аргументов, возвращающий int. `jfieldID` — идентификатор поля, не указатель для самостоятельного разыменования.

```sh
java -Xcheck:jni -Djava.library.path=. Main field
```

```text
Before: 42
After: 100
```

Меняется тот же Java-объект, копии Bean в C нет. JNI позволяет обращаться к `private`-полю. Сеттер не вызывается, поэтому его проверки и побочные действия, если бы они были, обходились бы. Для `null` устанавливается `NullPointerException`.

### Создать, прочитать и освободить нативную структуру

```c
typedef struct NativeData
{
    jint value;
} NativeData;
```

| Java-метод | Дескриптор | Действие в C |
|---|---|---|
| `long createNativeData(int value)` | `(I)J` | `malloc(sizeof(*data))`, запись поля, возврат адреса |
| `int readNativeData(long address)` | `(J)I` | Преобразование адреса в указатель, чтение `data->value` |
| `void freeNativeData(long address)` | `(J)V` | Преобразование адреса и `free(data)` |

`typedef` позволяет писать `NativeData` вместо `struct NativeData`. `sizeof(*data)` определяет размер по типу, не разыменовывая указатель во время выполнения. `data->value` эквивалентно `(*data).value`. `malloc` не вызывает конструктор и не инициализирует поле — значение записываем явно.

Адрес передаётся как `(jlong)(intptr_t)data` и восстанавливается как `(NativeData *)(intptr_t)address`. Java хранит непрозрачное число, а не управляемую ссылку на C-структуру.

```sh
java -Xcheck:jni -Djava.library.path=. Main struct
```

```text
Address: 0x...
Value: 42
NativeData freed
```

Адрес зависит от запуска. Java вызывает создание до `try`, читает значение внутри `try`, а освобождает структуру в `finally`. При неудаче создания устанавливается `OutOfMemoryError`; выполнение не доходит до блока работы со структурой.

Правила владения:

- Структура находится вне Java heap; GC её не освобождает.
- Успешное создание требует ровно одного освобождения.
- После `freeNativeData` адрес в Java-переменной может оставаться прежним, но использовать его уже нельзя.
- Повторный `free` и чтение после освобождения — undefined behavior.
- `readNativeData(0)` устанавливает `IllegalArgumentException`. Ненулевой адрес не обязательно корректен: проверка нуля не защищает от произвольного или устаревшего указателя.
- `freeNativeData(0)` допустим: `free(NULL)` ничего не делает.

Это учебный API с сырыми адресами. Его корректность зависит от соблюдения этих правил вызывающей стороной.

## Sentry и Gradle

### Конфигурация проекта

Используем бесплатный Developer-план и Error Monitoring. Java SDK — `io.sentry:sentry:8.61.1`, Sentry Native — `0.17.4`, Gradle Wrapper создан с Gradle `9.6.1`.

`build.gradle` подключает плагин `application`, зависимость SDK из Maven Central и Java toolchain 21. Java-файлы оставлены в корне `lab1`: `sourceSets` включает только `*.java` из этой папки. Главный класс — `Main`. Задача `run` задаёт `java.library.path` абсолютным путём к `lab1`.

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

cmake -S build/native-deps/sentry-native \
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

Прочие режимы также запускаем через Gradle, например:

```sh
./gradlew run --args="strings"
./gradlew run --args="call"
./gradlew run --args="field"
./gradlew run --args="struct"
```

Параметры `-Xms32m`, `-Xmx32m` или `-Xcheck:jni` из прежних экспериментов Gradle автоматически не добавляет. При необходимости их задают в `tasks.named('run')` через `jvmArgs`.

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

- [JNI: устройство, компиляция, загрузка, ссылки и исключения](https://docs.oracle.com/en/java/javase/21/docs/specs/jni/design.html)
- [javac, включая -h](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html)
- [Runtime и методы измерения памяти](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Runtime.html)
- [GCC: генерация отладочной информации через -g](https://gcc.gnu.org/onlinedocs/gcc/Debugging-Options.html)
- [Clang: флаги frame pointer и leaf frame pointer](https://clang.llvm.org/docs/ClangCommandLineReference.html)
- [Oracle: анализ нативных крешей](https://docs.oracle.com/en/java/javase/11/troubleshoot/troubleshoot-system-crashes.html)
- [Apple: расшифровка адресов через atos](https://developer.apple.com/documentation/xcode/adding-identifiable-symbol-names-to-a-crash-report)

- [Sentry Java SDK](https://github.com/getsentry/sentry-java)
- [Sentry Native: сборка и параметры](https://github.com/getsentry/sentry-native)
- [Sentry CLI через npm](https://www.npmjs.com/package/@sentry/cli)
