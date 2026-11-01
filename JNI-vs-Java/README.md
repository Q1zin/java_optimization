## Сравнение производительности на примере умножения матриц

Генерация `.h` файлов для `JNI` (после объявления `native` функции)
```bash
javac -h native/ MatrixMultiplication.java
```

Написание `MatrixMultiplication.c` файла, компиляция в динамическую библиотеку (macOS)
```bash
gcc -O2 -ffp-contract=off -fPIC -I $JAVA_HOME/include -I $JAVA_HOME/include/darwin -shared -o native/libMatrixMultiplication.dylib native/MatrixMultiplication.c
```

p.s. в моём случае `JAVA_HOME=$(/usr/libexec/java_home -v 21)`

- `-ffp-contract=off` - запрещает сливать `sum += a * b` в одну инструкцию FMA (`fmadd`). Java всегда считает умножение и сложение отдельно, с этим флагом C считает так же, и результаты совпадают до бита

- `-fno-vectorize` - отключает SIMD

Пишем основной модуль `Main.java`, загружаем в нём библиотеку `MatrixMultiplication` 
```bash
javac Main.java
```

Запускаем прогу
```bash
java --enable-native-access=ALL-UNNAMED -Djava.library.path=native Main
```

где `--enable-native-access=ALL-UNNAMED` - отвечает за разрешение вызовов нативного кода, а `-Djava.library.path=native` - где искать библиотеку (либо `DYLD_LIBRARY_PATH`).

- `-XX:+UseSuperWord` - включает SIMD, но ничего не изменится

## Прогрев JIT

`WarmupDemo` умножает матрицы подряд без прогрева и печатает время каждого запуска, а `-XX:+PrintCompilation` - моменты, когда JIT компилирует `multiply`
```bash
javac WarmupDemo.java
java -XX:+PrintCompilation -Djava.library.path=native WarmupDemo 64 20 | grep -E "^run|JavaMatrix::multiply"
```

В логе: первая колонка - мс от старта JVM, `%` - OSR (подмена кода прямо внутри работающего цикла), `3` - C1 (быстрый компилятор, собирает профиль), `4` - C2 (оптимизирующий), `made not entrant` - старая версия выброшена

Одно умножение при N=64:

| Режим | Время |
|---|---|
| `-Xint` (только интерпретатор) | ≈ 3.3 ms |
| `-XX:TieredStopAtLevel=1` (только C1) | ≈ 0.27 ms |
| по умолчанию, после прогрева (C2) | ≈ 0.17 ms |
| C | ≈ 0.12 ms с первого запуска |

Без прогрева первый запуск Java при N=64 в ~9 раз медленнее, поэтому `Main` перед замерами греет не меньше 3 запусков и не меньше 0.5 с: на маленьких N одно умножение быстрее, чем JIT успевает скомпилировать код

## Цена JNI-вызова

`JniCostDemo` 50 млн раз вызывает пустой native-метод `health()`, а затем умножает матрицы (`multiplyIkj`) на Java и на C для N от 64 до 1. Время одного вызова = общее время / число вызовов: один вызов короче шага `nanoTime` (~42 ns на macOS)
```bash
javac JniCostDemo.java
java -Djava.library.path=native JniCostDemo
```

Пустой JNI-вызов ≈ 3 ns (~10 тактов). Одно умножение:

| N | Java | C | доля JNI в C | t Java / t C |
|---|---|---|---|---|
| 64 | 93 µs | 38 µs | 0% | 2.49 |
| 32 | 12.6 µs | 6.0 µs | 0% | 2.10 |
| 16 | 1.9 µs | 1.1 µs | 0% | 1.70 |
| 8 | 329 ns | 232 ns | 1% | 1.42 |
| 4 | 73 ns | 71 ns | 4% | 1.02 |
| 2 | 14 ns | 14 ns | 23% | 1.00 |
| 1 | 5.2 ns | 7.3 ns | 43% | 0.70 |

- при N=1 Java быстрее: JIT встраивает Java-метод прямо в вызывающий цикл (inlining), а native-метод встроить нельзя - каждый вызов переключает поток из Java в native и обратно. С N=8 доля JNI меньше 1%, и C выигрывает за счёт SIMD
- матрицы уже лежат в C, поэтому копирование `fromArray`/`toArray` в замер не входит
- размеры идут от большого к маленькому: JIT компилирует `multiplyIkj` под те N, которые видел первыми. Если начать с N=1 (миллионы вызовов), код получится заточенным под циклы из одной итерации, и Java при N=64 станет в ~2.3 раза медленнее - проверить можно через `JniCostDemo 1 64`

Что видно в `-XX:+PrintCompilation`
```bash
java -XX:+PrintCompilation -Djava.library.path=native JniCostDemo 64 | grep -E "multiplyIkj|health"
```
```
     29   42     n 0       MatrixMultiplication::health (native)   (static)
    354  151 %     3       JavaMatrix::multiplyIkj @ 68 (126 bytes)
    355  152       3       JavaMatrix::multiplyIkj (126 bytes)
    355  153 %     4       JavaMatrix::multiplyIkj @ 68 (126 bytes)
    359  154       4       JavaMatrix::multiplyIkj (126 bytes)
    549  156     n 0       MatrixMultiplication::multiplyIkj (native)   (static)
```

- Java-метод `JavaMatrix::multiplyIkj` проходит обычный путь: C1 (`3`), потом C2 (`4`). C2-версию JIT может встроить прямо в вызывающий цикл
- у native-методов флаг `n` и уровень `0`: свой C-код JIT не компилирует (его уже собрал clang), а генерирует обёртку (native wrapper). Это небольшой кусок машинного кода, который на каждом вызове переключает поток из режима Java в native, передаёт аргументы в C-функцию и переключает обратно. Почти все ~3 ns пустого вызова - это работа обёртки
- обёртка появляется, когда native-метод начинают часто вызывать (`health()` - на 29 мс, `multiplyIkj` - на 549 мс, когда до него дошла очередь замеров). До этого переход делает интерпретатор

