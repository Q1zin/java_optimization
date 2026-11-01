#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "MatrixMultiplication.h"

typedef struct {
    int rows;
    int cols;
    // Элемент (i, j) хранится в data[i * cols + j]
    double *data;
} Matrix;

static Matrix *toMatrix(jlong handle) {
    return (Matrix *)(intptr_t)handle;
}

static void throwException(JNIEnv *env, const char *className, const char *message) {
    (*env)->ThrowNew(env, (*env)->FindClass(env, className), message);
}

JNIEXPORT jboolean JNICALL Java_MatrixMultiplication_health(JNIEnv *env, jclass clazz) {
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL Java_MatrixMultiplication_create(JNIEnv *env, jclass clazz, jint rows, jint cols) {
    if (rows <= 0 || cols <= 0) {
        throwException(env, "java/lang/IllegalArgumentException", "Размеры матрицы должны быть положительными");
        return 0;
    }

    Matrix *matrix = malloc(sizeof(Matrix));
    // calloc обнуляет память, как new double[] в Java
    double *data = calloc((size_t)rows * (size_t)cols, sizeof(double));

    if (matrix == NULL || data == NULL) {
        free(matrix);
        free(data);
        throwException(env, "java/lang/OutOfMemoryError", "Не хватило памяти под матрицу");
        return 0;
    }

    matrix->rows = rows;
    matrix->cols = cols;
    matrix->data = data;

    return (jlong)(intptr_t)matrix;
}

JNIEXPORT jlong JNICALL Java_MatrixMultiplication_fromArray(JNIEnv *env, jclass clazz, jint rows, jint cols, jdoubleArray values) {
    if ((*env)->GetArrayLength(env, values) != (jlong)rows * cols) {
        throwException(env, "java/lang/IllegalArgumentException", "values.length != rows * cols");
        return 0;
    }

    jlong handle = Java_MatrixMultiplication_create(env, clazz, rows, cols);

    if (handle == 0) {
        return 0;
    }

    (*env)->GetDoubleArrayRegion(env, values, 0, rows * cols, toMatrix(handle)->data);

    return handle;
}

JNIEXPORT jdoubleArray JNICALL Java_MatrixMultiplication_toArray(JNIEnv *env, jclass clazz, jlong handle) {
    Matrix *matrix = toMatrix(handle);
    jsize length = matrix->rows * matrix->cols;
    jdoubleArray values = (*env)->NewDoubleArray(env, length);

    if (values == NULL) {
        return NULL;
    }

    (*env)->SetDoubleArrayRegion(env, values, 0, length, matrix->data);

    return values;
}

static int checkSizes(JNIEnv *env, Matrix *a, Matrix *b, Matrix *c) {
    if (a->cols != b->rows || c->rows != a->rows || c->cols != b->cols) {
        throwException(env, "java/lang/IllegalArgumentException", "Несогласованные размеры матриц");
        return 0;
    }

    return 1;
}

// Порядок ijk: во внутреннем цикле B читается по столбцу, с шагом в целую строку
JNIEXPORT void JNICALL Java_MatrixMultiplication_multiply(JNIEnv *env, jclass clazz, jlong aHandle, jlong bHandle, jlong cHandle) {
    Matrix *a = toMatrix(aHandle);
    Matrix *b = toMatrix(bHandle);
    Matrix *c = toMatrix(cHandle);

    if (!checkSizes(env, a, b, c)) {
        return;
    }

    size_t n = a->rows;
    size_t m = a->cols;
    size_t p = b->cols;

    for (size_t i = 0; i < n; i++) {
        for (size_t j = 0; j < p; j++) {
            double sum = 0;

            for (size_t k = 0; k < m; k++) {
                sum += a->data[i * m + k] * b->data[k * p + j];
            }

            c->data[i * p + j] = sum;
        }
    }
}

// Порядок ikj: во внутреннем цикле B и C читаются по строке, подряд
static void multiplyIkjMatrices(Matrix *a, Matrix *b, Matrix *c) {
    size_t n = a->rows;
    size_t m = a->cols;
    size_t p = b->cols;

    memset(c->data, 0, n * p * sizeof(double));

    for (size_t i = 0; i < n; i++) {
        for (size_t k = 0; k < m; k++) {
            double aik = a->data[i * m + k];

            for (size_t j = 0; j < p; j++) {
                c->data[i * p + j] += aik * b->data[k * p + j];
            }
        }
    }
}

JNIEXPORT void JNICALL Java_MatrixMultiplication_multiplyIkj(JNIEnv *env, jclass clazz, jlong aHandle, jlong bHandle, jlong cHandle) {
    Matrix *a = toMatrix(aHandle);
    Matrix *b = toMatrix(bHandle);
    Matrix *c = toMatrix(cHandle);

    if (!checkSizes(env, a, b, c)) {
        return;
    }

    multiplyIkjMatrices(a, b, c);
}

// Умножает Java-массивы n×n без копирования: GetPrimitiveArrayCritical даёт указатель прямо на данные массива
JNIEXPORT void JNICALL Java_MatrixMultiplication_multiplyIkjArrays(JNIEnv *env, jclass clazz, jdoubleArray aArray, jdoubleArray bArray, jdoubleArray cArray, jint n) {
    jsize length = n * n;

    if ((*env)->GetArrayLength(env, aArray) != length || (*env)->GetArrayLength(env, bArray) != length
            || (*env)->GetArrayLength(env, cArray) != length) {
        throwException(env, "java/lang/IllegalArgumentException", "Длина массивов должна быть n * n");
        return;
    }

    // Пока массивы захвачены, другие JNI-функции вызывать нельзя, а GC ждёт: держим их только на время умножения
    double *a = (*env)->GetPrimitiveArrayCritical(env, aArray, NULL);
    double *b = (*env)->GetPrimitiveArrayCritical(env, bArray, NULL);
    double *c = (*env)->GetPrimitiveArrayCritical(env, cArray, NULL);

    if (a != NULL && b != NULL && c != NULL) {
        Matrix matrixA = { n, n, a };
        Matrix matrixB = { n, n, b };
        Matrix matrixC = { n, n, c };

        multiplyIkjMatrices(&matrixA, &matrixB, &matrixC);
    }

    // JNI_ABORT: A и B не менялись, и даже если JVM дала копию, записывать её обратно не нужно
    if (c != NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, cArray, c, 0);
    }
    if (b != NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, bArray, b, JNI_ABORT);
    }
    if (a != NULL) {
        (*env)->ReleasePrimitiveArrayCritical(env, aArray, a, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL Java_MatrixMultiplication_free(JNIEnv *env, jclass clazz, jlong handle) {
    Matrix *matrix = toMatrix(handle);

    if (matrix != NULL) {
        free(matrix->data);
        free(matrix);
    }
}
