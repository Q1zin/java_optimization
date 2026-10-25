## Первый пример с JNI

Генерация `.h` файлов для `JNI` (после объявления `native` функции)
```bash
javac -h . TestJNI.java
```

Написание `TestJNI.c` файла, компиляция в динамическую библиотеку (macOS)
```bash
gcc -fPIC -I $JAVA_HOME/include -I $JAVA_HOME/include/darwin -shared -o libTestJNINative.dylib TestJNI.c
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

