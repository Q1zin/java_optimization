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

