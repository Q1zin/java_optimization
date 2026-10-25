#include "TestJNI.h"
#include <stdio.h>

JNIEXPORT jint JNICALL Java_TestJNI_printf(JNIEnv *env, jclass cl, jstring jstr)
{
    const char *cstr = (*env)->GetStringUTFChars(env, jstr, NULL);
    int numchars = printf("%s", cstr);
    (*env)->ReleaseStringUTFChars(env, jstr, cstr);
    return numchars;
}