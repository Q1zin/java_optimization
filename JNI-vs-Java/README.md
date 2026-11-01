## Сравнение производительности на примере умножения матриц

Генерация `.h` файлов для `JNI` (после объявления `native` функции)
```bash
javac -h native/ MatrixMultiplication.java
```

Написание `MatrixMultiplication.c` файла, компиляция в динамическую библиотеку (macOS)
```bash
gcc -O2 -fPIC -I $JAVA_HOME/include -I $JAVA_HOME/include/darwin -shared -o native/libMatrixMultiplication.dylib native/MatrixMultiplication.c
```

p.s. в моём случае `JAVA_HOME=$(/usr/libexec/java_home -v 21)`

Пишем основной модуль `Main.java`, загружаем в нём библиотеку `MatrixMultiplication` 
```bash
javac Main.java
```

Запускаем прогу
```bash
java --enable-native-access=ALL-UNNAMED -Djava.library.path=native Main
```

где `--enable-native-access=ALL-UNNAMED` - отвечает за разрешение вызовов нативного кода, а `-Djava.library.path=native` - где искать библиотеку (либо `DYLD_LIBRARY_PATH`).

