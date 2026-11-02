#!/bin/bash
# Собирает C-библиотеку с разными флагами компилятора и прогоняет Main на одном N, потом - Java в разных режимах JVM.
# Каждая сборка C кладётся во временную папку, основная библиотека в native/ не меняется.
# Запуск: ./flags.sh [N], по умолчанию N=256. Компилятор - из CC (по умолчанию gcc; на macOS это clang)
# stdout.encoding=UTF-8 - чтобы на Windows кириллица не ломалась, когда вывод идёт в pipe
set -e
cd "$(dirname "$0")"

case "$(uname -s)" in
    MINGW* | MSYS*)
        JAVA_HOME=${JAVA_HOME:?"укажите JAVA_HOME"}
        LIBRARY=MatrixMultiplication.dll
        PLATFORM=win32
        ;;
    *)
        JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}
        LIBRARY=libMatrixMultiplication.dylib
        PLATFORM=darwin
        ;;
esac

CC=${CC:-gcc}
N=${1:-256}

javac Main.java

run() {
    echo "=== $CC $*"

    dir=$(mktemp -d)
    "$CC" "$@" -fPIC -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/$PLATFORM" -shared \
        -o "$dir/$LIBRARY" native/MatrixMultiplication.c

    java -Dstdout.encoding=UTF-8 -Djava.library.path="$dir" Main "$N" | sed -n '/^ *N |/,$p'
    rm -rf "$dir"
}

run -O0 -ffp-contract=off
run -O1 -ffp-contract=off
run -O2 -ffp-contract=off
run -O3 -ffp-contract=off
run -O2 -ffp-contract=off -fno-tree-vectorize
run -O2
run -O2 -ffast-math

# На x86 без -march=native компилятор берёт только базовый набор x86-64 (SSE2): 2 double за инструкцию и без FMA
if [ "$(uname -m)" = x86_64 ]; then
    run -O2 -ffp-contract=off -march=native
    run -O2 -march=native
fi

runJava() {
    echo "=== java $*"
    java "$@" -Dstdout.encoding=UTF-8 -Djava.library.path=native Main "$N" | sed -n '/^ *N |/,$p'
}

runJava -Xint
runJava -XX:TieredStopAtLevel=1
runJava -XX:-UseSuperWord
