package video.lexo.decrypt.ttplayer

import android.app.Activity
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.Surface
import android.view.TextureView
import android.view.TextureView.SurfaceTextureListener
import com.pandora.common.env.Env
import com.pandora.common.env.config.Config
import com.ss.ttm.player.BufferProcessCallback
import com.ss.ttvideoengine.DataLoaderHelper
import com.ss.ttvideoengine.TTVideoEngine
import com.ss.ttvideoengine.TTVideoEngineInterface
import com.ss.ttvideoengine.source.DirectUrlSource
import video.lexo.decrypt.DecryptResult
import video.lexo.decrypt.DecryptState
import video.lexo.decrypt.PlayerDecryptor
import java.nio.ByteBuffer
import java.util.Collections


class PlayerActivity : Activity() {

    private companion object {
        private const val TAG = "PlayerActivity"

        //Pass in the IV related to encryption and decryption
        private const val DECRYPT_IV = "xxxxxxx"
    }

    private val sessionMap = Collections.synchronizedMap(HashMap<String, UrlProtocolSession>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        TTVideoEngine.setIntValue(DataLoaderHelper.DATALOADER_KEY_INT_ENABLE_HLS, 1)
        TTVideoEngine.setIntValue(DataLoaderHelper.DATALOADER_KEY_ENABLE_HLS_PROXY, 1)

        val config = Config.Builder().setApplicationContext(this.applicationContext)
            //Set TT-Player Appid
            .setAppID("TT-Player Appid")
            .setAppChannel("lexo")
            //Copy the TT-Player license file to the app's assets folder and set LicenseUri
            .setLicenseUri("assets:///tt-player/vod.lic").build()
        Env.init(config)

        val player = TTVideoEngine(this, TTVideoEngine.PLAYER_TYPE_OWN)

        player.setIntOption(TTVideoEngine.PLAYER_OPTION_ALLOW_ALL_PROTO_NAME, 1)
        player.setStringOption(TTVideoEngine.PLAYER_OPTION_BUFFER_PROCESS_PROTO_NAME, TAG)
        player.setStringOption(TTVideoEngine.PLAYER_OPTION_BUFFER_PROCESS_COVERT_ORDER, "21")
        player.setBufferProcessCallback(object : BufferProcessCallback() {

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

            override fun seeked(url: String?, ret: Long, where: Int) {

            }

            override fun closed(url: String?, ret: Int) {
                if (url.isNullOrEmpty()) return
                val session = sessionMap.remove(url)
                session?.decryptor?.release()
            }
        })

        val encryptedVideoUrl = "https://xxx.m3u8"
        val urlItem = DirectUrlSource.UrlItem.Builder().setUrl(encryptedVideoUrl)
            .setCacheKey(encryptedVideoUrl).build()
        val source = DirectUrlSource.Builder().setVid(encryptedVideoUrl).addItem(urlItem).build()
        player.strategySource = source

        val textureView = findViewById<TextureView>(R.id.texture_view)
        textureView.surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                player.surface = Surface(surfaceTexture)
            }

            override fun onSurfaceTextureSizeChanged(
                surfaceTexture: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
            }
        }

        player.setDisplayMode(textureView, TTVideoEngineInterface.IMAGE_LAYOUT_ASPECT_FILL)
        player.setIntOption(TTVideoEngine.PLAYER_OPTION_INT_ALLOW_ALL_EXTENSIONS, 1)
        player.play()
    }
}