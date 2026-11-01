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
- матрицы уже лежат в C, поэтому копирование `fromArray`/`toArray` в замер не входит (оно - в `CopyCostDemo`, ниже)
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

## Копирование данных Java → C

Если данные живут в Java, их надо как-то отдать в C. `CopyCostDemo` сравнивает для N от 512 до 1 четыре способа умножить матрицы (ikj):
- Java
- C - матрицы уже лежат в C (как в `JniCostDemo`)
- C + копия - на каждом вызове `fromArray` копирует A и B в C, после умножения `toArray` копирует результат обратно, C-матрицы освобождаются
- C без копии - `multiplyIkjArrays` получает сами Java-массивы, `GetPrimitiveArrayCritical` даёт C указатель прямо на их данные. Умножает тот же C-код, что и `multiplyIkj`
```bash
javac CopyCostDemo.java
java -Djava.library.path=native CopyCostDemo
```

| N | Java | C | C + копия | C без копии | t Java / t C+копия | t Java / t C без копии |
|---|---|---|---|---|---|---|
| 512 | 45.4 ms | 21.0 ms | 22.2 ms | 20.7 ms | 2.04 | 2.20 |
| 256 | 5.52 ms | 2.66 ms | 2.80 ms | 2.55 ms | 1.97 | 2.17 |
| 128 | 713 µs | 298 µs | 356 µs | 292 µs | 2.00 | 2.45 |
| 64 | 90 µs | 38 µs | 56 µs | 35 µs | 1.61 | 2.57 |
| 32 | 12.4 µs | 6.0 µs | 8.8 µs | 6.8 µs | 1.41 | 1.82 |
| 16 | 1.9 µs | 1.1 µs | 2.2 µs | 1.5 µs | 0.85 | 1.22 |
| 8 | 339 ns | 229 ns | 1.0 µs | 691 ns | 0.33 | 0.49 |
| 4 | 78 ns | 72 ns | 719 ns | 533 ns | 0.11 | 0.15 |
| 2 | 14 ns | 14 ns | 637 ns | 459 ns | 0.02 | 0.03 |
| 1 | 5.4 ns | 7.5 ns | 592 ns | 453 ns | 0.01 | 0.01 |

Из чего складывается копия при N=1 (печатается в конце):

| Операция | Время |
|---|---|
| `create` + `free` (malloc/calloc и free в C) | 58 ns |
| `fromArray` + `free` (ещё `GetArrayLength` и `GetDoubleArrayRegion`) | 176 ns |
| `toArray` (`NewDoubleArray` + `SetDoubleArrayRegion`) | 122 ns |
| `new double[1]` в Java | 4.7 ns |

- граница сдвигается: данные уже в C - C выгоден с N≈4-8; без копии - с N≈16; с копией - только с N≈32. На маленьких N Java быстрее всех
- копирование растёт как N², а умножение как N³: при N=64 копия добавляет ~50% к времени C, при N=512 - ~6%
- без копии на больших N так же быстро, как если бы данные уже лежали в C, а результат совпадает с Java до бита
- на маленьких N дорог не сам JNI-переход (~3 ns), а JNI-функции, которые работают с Java-объектами (`GetArrayLength`, `GetDoubleArrayRegion`, `NewDoubleArray`, `Get/ReleasePrimitiveArrayCritical`): каждая заходит внутрь JVM и стоит ~50-60 ns. Это видно по двум замерам: `fromArray` дороже `create` на ~120 ns за 2 такие функции, а «C без копии» при N=1 - ~450 ns за 9 таких функций
- создать Java-массив из C (`toArray`) в ~25 раз дороже, чем `new double[]` в самой Java
- пока массивы захвачены через `GetPrimitiveArrayCritical`, другие JNI-функции вызывать нельзя, а сборщик мусора ждёт, поэтому захватывать их стоит только на время самого умножения

## Флаги компилятора и режимы JVM

`flags.sh` собирает C-библиотеку с разными флагами clang (каждую - во временную папку, основная `native/libMatrixMultiplication.dylib` не меняется) и прогоняет `Main`, потом запускает Java в разных режимах JVM. По умолчанию N=256
```bash
./flags.sh        # или ./flags.sh 512
```

C, N=256 (Java для сравнения: ijk ≈ 13.9 ms, ikj ≈ 5.8 ms):

| Флаги clang | C ijk | C ikj | Совпадает с Java |
|---|---|---|---|
| `-O0 -ffp-contract=off` | 58.6 ms | 30.1 ms | да |
| `-O1 -ffp-contract=off` | 12.8 ms | 5.89 ms | да |
| `-O2 -ffp-contract=off` | 13.0 ms | 2.63 ms | да |
| `-O3 -ffp-contract=off` | 12.7 ms | 2.56 ms | да |
| `-O2 -ffp-contract=off -fno-vectorize` | 13.0 ms | 5.90 ms | да |
| `-O2` (с FMA) | 15.6 ms | 2.66 ms | нет, разница ~4e-14 |
| `-O2 -ffast-math` | 15.3 ms | 2.65 ms | нет, разница ~4e-14 |

- `-O0` в 4-11 раз медленнее, уже `-O1` догоняет Java. Векторизация включается с `-O2` (ikj 5.9 → 2.6 ms), и это весь отрыв C от Java в ikj. `-O3` ≈ `-O2`
- FMA в ijk замедляет (15.6 против 13.0 ms): сумма копится цепочкой, и каждая итерация ждёт `fmadd`, а она дольше обычного сложения. В ikj FMA ничего не меняет: там у каждого `c[j]` своя сумма, цепочки нет, и упираемся в память
- `-ffast-math` ничего не дал. В ijk clang сделал векторную версию цикла только на случай, когда у B одна колонка (тогда столбец лежит в памяти подряд), и проверяет это перед циклом. При N=256 работает обычный скалярный цикл, а `-ffast-math` поменял только векторную версию

Что clang решил векторизовать, он сам пишет с флагом `-Rpass`
```bash
gcc -O2 -ffp-contract=off -Rpass=loop-vectorize -I $JAVA_HOME/include -I $JAVA_HOME/include/darwin -c -o /dev/null native/MatrixMultiplication.c
```

Java, N=256:

| Режим JVM | Java ijk | Java ikj |
|---|---|---|
| `-Xint` (только интерпретатор) | 209 ms | 232 ms |
| `-XX:TieredStopAtLevel=1` (только C1) | 17.6 ms | 19.3 ms |
| по умолчанию (C2) | 13.9 ms | 5.8 ms |
| `-XX:-UseSuperWord` (C2 без автовекторизации) | 13.7 ms | 5.9 ms |

- в интерпретаторе и в C1 ikj даже медленнее ijk: в его внутреннем цикле больше операций с памятью (прочитать и записать `c[j]`), а без оптимизаций каждая из них дорогая. Выигрыш от порядка циклов появляется, только когда код уже настолько быстрый, что упирается в память
- `-XX:-UseSuperWord` ничего не меняет: JIT эти циклы и так не векторизует

### Почему JIT не векторизует ikj

`VectorizeDemo` сравнивает три цикла вида `c[...] += s * b[...]`
```bash
javac VectorizeDemo.java
java VectorizeDemo
java -XX:-UseSuperWord VectorizeDemo
```

| Цикл | по умолчанию | `-XX:-UseSuperWord` |
|---|---|---|
| `c[j] += s * b[j]` | 0.16 ns/элемент | 0.27 ns/элемент |
| `c[off + j] += s * b[off + j]` (одинаковые смещения) | 0.24 ns/элемент | 0.37 ns/элемент |
| `c[off1 + j] += s * b[off2 + j]` (разные смещения, как в `multiplyIkj`) | 0.41 ns/элемент | 0.41 ns/элемент |

- первые два цикла JIT векторизует: без SuperWord они медленнее. Третий - нет: время одинаковое
- причина - возможное пересечение массивов. Если `c` и `b` - один и тот же массив, а смещения разные, запись в `c[off1 + j]` может изменить то, что следующие итерации прочитают из `b[off2 + j]`. JIT в JDK 21 не может доказать, что это разные массивы, и не векторизует
- clang в той же ситуации вставляет перед векторным циклом проверку во время выполнения (в LLVM IR это блоки `vector.memcheck`): если массивы пересекаются, работает обычный цикл, если нет - векторный

