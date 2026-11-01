## Первый пример с JNI и Rust

Реализация `TestJNI.printf` находится в `src/lib.rs`, настройки библиотеки и зависимость `jni` — в `Cargo.toml`.

Собираем динамическую библиотеку (на macOS — `target/release/libtest_jni_native.dylib`):
```bash
cargo build --release
```

Компилируем Java:
```bash
javac Main.java TestJNI.java
```

Запускаем прогу:
```bash
java -Djava.library.path=target/release Main
```


где `--enable-native-access=ALL-UNNAMED` - отвечает за разрешение вызовов нативного кода, а `-Djava.library.path=.` - где искать библиотеку (либо `DYLD_LIBRARY_PATH`).

