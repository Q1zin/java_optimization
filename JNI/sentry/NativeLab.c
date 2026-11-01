#include "NativeLab.h"
#include <stdlib.h>
#include <stdio.h>
#include <sentry.h>

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

JNIEXPORT jint JNICALL Java_NativeLab_initNativeSentry(
    JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;

    const char *dsn = getenv("SENTRY_DSN");
    const char *handler = getenv("SENTRY_NATIVE_HANDLER");
    const char *database = getenv("SENTRY_NATIVE_DATABASE");

    if (dsn == NULL || *dsn == '\0' ||
        handler == NULL || *handler == '\0' ||
        database == NULL || *database == '\0')
    {
        fprintf(stderr, "Missing Sentry Native environment variables\n");
        return -1;
    }

    sentry_options_t *options = sentry_options_new();

    sentry_options_set_dsn(options, dsn);
    sentry_options_set_handler_path(options, handler);
    sentry_options_set_database_path(options, database);
    sentry_options_set_release(options, "jni-lab1@1.0");
    sentry_options_set_environment(options, "local");
    sentry_options_set_debug(options, 1);

    return sentry_init(options);
}
