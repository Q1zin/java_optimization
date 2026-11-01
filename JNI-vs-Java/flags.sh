#!/bin/bash
# Собирает C-библиотеку с разными флагами clang и прогоняет Main на одном N, потом - Java в разных режимах JVM.
# Каждая сборка C кладётся во временную папку, основная native/libMatrixMultiplication.dylib не меняется.
# Запуск: ./flags.sh [N], по умолчанию N=256
set -e
cd "$(dirname "$0")"

JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}
N=${1:-256}

javac Main.java

run() {
    echo "=== clang $*"

    dir=$(mktemp -d)
    gcc "$@" -fPIC -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/darwin" -shared \
        -o "$dir/libMatrixMultiplication.dylib" native/MatrixMultiplication.c

    java -Djava.library.path="$dir" Main "$N" | sed -n '/^ *N |/,$p'
    rm -rf "$dir"
}

run -O0 -ffp-contract=off
run -O1 -ffp-contract=off
run -O2 -ffp-contract=off
run -O3 -ffp-contract=off
run -O2 -ffp-contract=off -fno-vectorize
run -O2
run -O2 -ffast-math

runJava() {
    echo "=== java $*"
    java "$@" -Djava.library.path=native Main "$N" | sed -n '/^ *N |/,$p'
}

runJava -Xint
runJava -XX:TieredStopAtLevel=1
runJava -XX:-UseSuperWord
