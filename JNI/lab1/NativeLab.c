#include "NativeLab.h"
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

JNIEXPORT jlong JNICALL Java_NativeLab_allocateKilobyte(
    JNIEnv *env, jclass clazz)
{
    (void)clazz;

    void *memory = malloc(1024);

    if (memory == NULL)
    {
        jclass errorClass = (*env)->FindClass(
            env, "java/lang/OutOfMemoryError");

        if (errorClass != NULL)
        {
            (*env)->ThrowNew(
                env, errorClass, "Cannot allocate 1024 native bytes");
        }

        return 0;
    }

    memset(memory, 0xAB, 1024);

    return (jlong)(intptr_t)memory;
}

JNIEXPORT void JNICALL Java_NativeLab_freeMemory(
    JNIEnv *env, jclass clazz, jlong address)
{
    (void)env;
    (void)clazz;

    free((void *)(intptr_t)address);
}

static void levelThree(void)
{
    volatile int *pointer = NULL;
    *pointer = 42; // ошибка
}

static void levelTwo(void)
{
    levelThree();
}

static void levelOne(void)
{
    levelTwo();
}

JNIEXPORT void JNICALL Java_NativeLab_crashInCallChain(
    JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;

    levelOne();
}

JNIEXPORT void JNICALL Java_NativeLab_consumeMemory(
    JNIEnv *env, jclass clazz)
{
    (void)clazz;

    const size_t blockSize = (size_t)1 << 40; // 1 ТиБ
    void *blocks[4096];
    size_t count = 0;

    while (count < 4096)
    {
        void *block = malloc(blockSize);

        if (block == NULL)
        {
            break;
        }

        blocks[count++] = block;
        fprintf(stderr, "Allocated: %zu TiB of virtual memory\n", count);
    }

    int allocationFailed = count < 4096;

    for (size_t i = 0; i < count; i++)
    {
        free(blocks[i]);
    }

    if (allocationFailed)
    {
        jclass errorClass = (*env)->FindClass(
            env, "java/lang/OutOfMemoryError");

        if (errorClass != NULL)
        {
            (*env)->ThrowNew(
                env, errorClass,
                "Native malloc failed to allocate another 1 TiB block");
        }

        return;
    }

    fprintf(stderr, "Experiment stopped: pointer storage is full\n");
}

typedef struct MemoryBlock
{
    struct MemoryBlock *next;
    volatile unsigned char data[1024 * 1024];
} MemoryBlock;

JNIEXPORT void JNICALL Java_NativeLab_pressureMemory(
    JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;

    MemoryBlock *head = NULL;
    size_t count = 0;

    for (;;)
    {
        MemoryBlock *block = malloc(sizeof(*block));

        if (block == NULL)
        {
            fprintf(stderr, "malloc returned NULL after %zu MiB\n", count);
            break;
        }

        block->next = head;
        head = block;

        for (size_t i = 0; i < sizeof(block->data); i++)
        {
            block->data[i] = (unsigned char)(i + count);
        }

        count++;

        if (count % 64 == 0)
        {
            fprintf(stderr, "Touched and retained: %zu MiB\n", count);
        }
    }

    while (head != NULL)
    {
        MemoryBlock *next = head->next;
        free(head);
        head = next;
    }
}

JNIEXPORT jint JNICALL Java_NativeLab_stringLength(
    JNIEnv *env, jclass clazz, jstring text)
{
    (void)clazz;

    if (text == NULL)
    {
        jclass exceptionClass = (*env)->FindClass(
            env, "java/lang/NullPointerException");

        if (exceptionClass != NULL)
        {
            (*env)->ThrowNew(
                env, exceptionClass, "text must not be null");
        }

        return 0;
    }

    return (*env)->GetStringLength(env, text);
}

JNIEXPORT void JNICALL Java_NativeLab_callBeanMethod(
    JNIEnv *env, jclass clazz, jobject bean)
{
    (void)clazz;

    if (bean == NULL)
    {
        jclass exceptionClass = (*env)->FindClass(
            env, "java/lang/NullPointerException");

        if (exceptionClass != NULL)
        {
            (*env)->ThrowNew(
                env, exceptionClass, "bean must not be null");
        }

        return;
    }

    jclass beanClass = (*env)->GetObjectClass(env, bean);

    if (beanClass == NULL)
    {
        return;
    }

    jmethodID getter = (*env)->GetMethodID(
        env, beanClass, "getIntValue", "()I");

    if (getter == NULL)
    {
        return;
    }

    jint value = (*env)->CallIntMethod(env, bean, getter);

    if ((*env)->ExceptionCheck(env))
    {
        return;
    }

    printf("C called Bean.getIntValue(): %d\n", (int)value);
    fflush(stdout);
}

JNIEXPORT void JNICALL Java_NativeLab_changeBeanInt(
    JNIEnv *env, jclass clazz, jobject bean, jint value)
{
    (void)clazz;

    if (bean == NULL)
    {
        jclass exceptionClass = (*env)->FindClass(
            env, "java/lang/NullPointerException");

        if (exceptionClass != NULL)
        {
            (*env)->ThrowNew(
                env, exceptionClass, "bean must not be null");
        }

        return;
    }

    jclass beanClass = (*env)->GetObjectClass(env, bean);

    if (beanClass == NULL)
    {
        return;
    }

    jfieldID field = (*env)->GetFieldID(
        env, beanClass, "intValue", "I");

    if (field == NULL)
    {
        return;
    }

    (*env)->SetIntField(env, bean, field, value);
}

typedef struct NativeData
{
    jint value;
} NativeData;

JNIEXPORT jlong JNICALL Java_NativeLab_createNativeData(
    JNIEnv *env, jclass clazz, jint value)
{
    (void)clazz;

    NativeData *data = malloc(sizeof(*data));

    if (data == NULL)
    {
        jclass exceptionClass = (*env)->FindClass(
            env, "java/lang/OutOfMemoryError");

        if (exceptionClass != NULL)
        {
            (*env)->ThrowNew(
                env, exceptionClass, "Cannot allocate NativeData");
        }

        return 0;
    }

    data->value = value;

    return (jlong)(intptr_t)data;
}

JNIEXPORT jint JNICALL Java_NativeLab_readNativeData(
    JNIEnv *env, jclass clazz, jlong address)
{
    (void)clazz;

    if (address == 0)
    {
        jclass exceptionClass = (*env)->FindClass(
            env, "java/lang/IllegalArgumentException");

        if (exceptionClass != NULL)
        {
            (*env)->ThrowNew(
                env, exceptionClass, "NativeData address must not be zero");
        }

        return 0;
    }

    NativeData *data = (NativeData *)(intptr_t)address;

    return data->value;
}

JNIEXPORT void JNICALL Java_NativeLab_freeNativeData(
    JNIEnv *env, jclass clazz, jlong address)
{
    (void)env;
    (void)clazz;

    NativeData *data = (NativeData *)(intptr_t)address;

    free(data);
}
