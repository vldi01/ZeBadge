package de.berlindroid.zeapp.zeservices

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import de.berlindroid.zeapp.zeui.pixelBuffer
import de.berlindroid.zekompanion.*
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject

private const val SPACE_REPLACEMENT = "\$SPACE#"

class ZeBadgeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val badgeConfigParser: ZeBadgeConfigParser,
) {

    private val badgeManager = buildBadgeManager(Environment(context))

    /**
     * Send a bitmap to the badge to be shown instantaneous
     *
     * @param page the bitmap in black / white to be send to the badge
     */
    suspend fun previewPage(page: Bitmap): Result<Int> {
        val binaryPayload = page
            .pixelBuffer()
            .toBinary()
            .zipit()
            .base64()

        val payload = PreviewLoad(
            meta = "",
            payload = binaryPayload,
        )

        return badgeManager.sendPayload(payload)
    }

    /**
     * Store a bitmap on the badge
     *
     * @param name a file name on the badge to be stored
     * @param page the bitmap in black / white to be sent to the badge
     */
    suspend fun storePage(name: String, page: Bitmap): Result<Int> {
        val binaryPayload = page
            .pixelBuffer()
            .toBinary()
            .zipit()
            .base64()

        val payload = StoreLoad(
            meta = name,
            payload = binaryPayload,
        )

        return badgeManager.sendPayload(payload)
    }

    /**
     * Show a stored b64 on the badge
     *
     * @param name a file name on the badge to be shown
     */
    suspend fun showPage(name: String): Result<Int> {
        val payload = ShowLoad(
            meta = name,
            payload = "",
        )

        return badgeManager.sendPayload(payload)
    }

    /**
     * Return the name of the pages stored on the badge.
     */
    suspend fun requestPagesStored(): Result<String> {
        val payload = ListLoad(
            meta = "",
            payload = "",
        )

        return if (badgeManager.sendPayload(payload).isSuccess) {
            badgeManager.readResponse()
        } else {
            Result.failure(NoSuchElementException())
        }
    }

    /**
     * Return the current active configuration.
     */
    suspend fun listConfiguration(): Result<Map<String, Any?>> {
        badgeManager.sendPayload(
            ConfigLoad(
                meta = "",
                payload = "",
            ),
        )

        badgeManager.readResponse()
        delay(300)

        val payload = ConfigListLoad(
            meta = "",
            payload = "",
        )

        if (badgeManager.sendPayload(payload).isSuccess) {
            val response = badgeManager.readResponse()
            delay(300)

            if (response.isSuccess) {
                val config = response.getOrDefault("").replace("\r\n", "")
                Timber.v(
                    "Badge sent response: successfully received configuration: " +
                            "'${config.replace("\n", "\\n")}'.",
                )

                val parseResult = badgeConfigParser.parse(config)
                Timber.v("Badge config parsed: $parseResult")

                return Result.success(parseResult.flatten())
            }
            return Result.failure(IllegalStateException("Could not read response."))
        } else {
            return Result.failure(NoSuchElementException("Sending command failed."))
        }
    }

    /**
     * Update configuration on badge..
     */
    suspend fun updateConfiguration(configuration: Map<String, Any?>): Result<Any> {

        val detypedConfig: Map<String, String> = configuration.map { e ->
            val (k, v) = e
            k to kotlinToPython(v)
        }.toMap()

        val config = detypedConfig.entries.joinToString(separator = " ")

        val payload = ConfigUpdateLoad(
            meta = "",
            payload = config,
        )

        if (badgeManager.sendPayload(payload).isSuccess) {
            badgeManager.readResponse()
            delay(300)

            return if (badgeManager.sendPayload(
                    ConfigSaveLoad(
                        meta = "",
                        payload = "",
                    ),
                ).isSuccess
            ) {
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("Could not save the config to ZeBadge."))
            }
        } else {
            return Result.failure(NoSuchElementException("Could not update the runtime configuration on ZeBadge."))
        }
    }

    fun isConnected(): Boolean = badgeManager.isConnected()
}

private fun kotlinToPython(value: Any?): String = when (value) {
    null -> "None"
    is String -> value.replace(" ", SPACE_REPLACEMENT)
    is Boolean -> if (value) "True" else "False"
    else -> "$value"
}
