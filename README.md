# ttplayer-decrypt-kotlin

Maven repository
```kotlin   
repositories {
    maven { url 'https://maven.lexo.video/repository/maven-releases/' }
    maven { url 'https://artifact.bytedance.com/repository/Volcengine/') }
}
```

```kotlin
implementation 'video.lexo.decrypt:player:2.1.6'
implementation 'com.bytedanceapi:ttsdk-player_premium:1.42.3.103'
```

Decrypt Data
```kotlin
override fun processBuffer(url: String?, data: ByteBuffer?): ProcessBufferResult {
    val session = sessionMap[url]
    if (session == null) {
        val result = ProcessBufferResult()
        result.ret = -12
        return result
    } else {
        //Pass in the encrypted data and get the decrypted data
        val process = session.decryptor.process(data)
        val result = ProcessBufferResult()
        if (process.result == DecryptResult.RESULT_EAGAIN) {
            result.ret = ProcessBufferResult.EAGAIN
        } else if (process.result == DecryptResult.RESULT_EOF) {
            result.ret = ProcessBufferResult.EOF
        } else {
            result.ret = process.result
            result.buffer = process.data
        }
        return result
    }
}

override fun isChunk(url: String?): Boolean {
    return true
}

override fun opened(url: String?, ret: Int) {
    if (url.isNullOrEmpty()) return
    var session = sessionMap[url]
    if (session != null) return
    val decryptor = PlayerDecryptor(this@PlayerActivity, DECRYPT_IV)
    session = UrlProtocolSession(decryptor)
    sessionMap[url] = session
}

override fun readed(url: String?, ret: Int) {
    val session = sessionMap[url] ?: return
    if (session.ret == -10000) {
        session.decryptor.updateState(DecryptState.START)
    }
    if (ret == ProcessBufferResult.EOF) {
        session.decryptor.updateState(DecryptState.END)
    }
    session.ret = ret
}
```