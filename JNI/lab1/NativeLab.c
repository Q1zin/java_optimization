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