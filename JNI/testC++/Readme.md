## Пример с JNI с C++

Генерация `.h` файлов для `JNI` (после объявления `native` функции)
```bash
javac -h . TestJNI.java
```

Написание `TestJNI.cpp` файла, компиляция в динамическую библиотеку (macOS)
```bash
clang++ -std=c++20 -fPIC -I $JAVA_HOME/include -I $JAVA_HOME/include/darwin -shared -o libTestJNINative.dylib TestJNI.cpp
```

p.s. в моём случае `JAVA_HOME=$(/usr/libexec/java_home -v 21)`

Пишем основной модуль `Main.java`, загружаем в нём библиотеку `TestJNINative` 
```bash
javac Main.java
```

Запускаем прогу
```bash
java --enable-native-access=ALL-UNNAMED -Djava.library.path=. Main
```

где `--enable-native-access=ALL-UNNAMED` - отвечает за разрешение вызовов нативного кода, а `-Djava.library.path=.` - где искать библиотеку (либо `DYLD_LIBRARY_PATH`).

