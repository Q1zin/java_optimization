use jni::EnvUnowned;
use jni::objects::{JClass, JString};
use jni::sys::jint;
use std::io::{self, Write};

#[unsafe(no_mangle)]
pub extern "system" fn Java_TestJNI_printf<'local>(
    mut env: EnvUnowned<'local>,
    _class: JClass<'local>,
    text: JString<'local>,
) -> jint {
    env.with_env(
        |_env: &mut jni::Env<'_>| -> Result<jint, jni::errors::Error> {
            let text = text.to_string();
            let mut stdout = io::stdout().lock();
            if stdout
                .write_all(text.as_bytes())
                .and_then(|()| stdout.flush())
                .is_err()
            {
                return Ok(-1);
            }
            Ok(text.len() as jint)
        },
    )
    .resolve::<jni::errors::ThrowRuntimeExAndDefault>()
}
