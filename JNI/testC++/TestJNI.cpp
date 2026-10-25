#include "TestJNI.h"
#include <stdio.h>

JNIEXPORT jint JNICALL Java_TestJNI_printf(JNIEnv *env, jclass cl, jstring jstr)
{
    const char *cstr = env->GetStringUTFChars(jstr, nullptr);
    if (cstr == nullptr)
    {
        return 0;
    }

    int numchars = printf("%s", cstr);
    env->ReleaseStringUTFChars(jstr, cstr);

    return numchars;
}