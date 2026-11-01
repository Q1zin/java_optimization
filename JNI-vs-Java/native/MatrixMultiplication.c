#include <stdint.h>
#include <stdlib.h>

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

JNIEXPORT void JNICALL Java_MatrixMultiplication_fillRandom(JNIEnv *env, jclass clazz, jlong handle, jlong seed) {
    Matrix *matrix = toMatrix(handle);
    size_t count = (size_t)matrix->rows * matrix->cols;
    // erand48 хранит состояние в переданном массиве, а не в глобальной переменной, как rand()
    unsigned short state[3] = { (unsigned short)seed, (unsigned short)(seed >> 16), (unsigned short)(seed >> 32) };

    for (size_t i = 0; i < count; i++) {
        matrix->data[i] = erand48(state);
    }
}

JNIEXPORT void JNICALL Java_MatrixMultiplication_multiply(JNIEnv *env, jclass clazz, jlong aHandle, jlong bHandle, jlong cHandle) {
    Matrix *a = toMatrix(aHandle);
    Matrix *b = toMatrix(bHandle);
    Matrix *c = toMatrix(cHandle);

    if (a->cols != b->rows || c->rows != a->rows || c->cols != b->cols) {
        throwException(env, "java/lang/IllegalArgumentException", "Несогласованные размеры матриц");
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

JNIEXPORT void JNICALL Java_MatrixMultiplication_free(JNIEnv *env, jclass clazz, jlong handle) {
    Matrix *matrix = toMatrix(handle);

    if (matrix != NULL) {
        free(matrix->data);
        free(matrix);
    }
}
