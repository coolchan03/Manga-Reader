package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonArray
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** `log`, and the crypto helpers of Mangayomi's JS runtime (eval/javascript/utils.dart). */
internal class JsUtilsBridge(private val logger: JsLogger) {

	fun handle(name: String, a: JsonArray): Any? = when (name) {
		"log" -> {
			logger.log(a.getOrNull(0).str().orEmpty())
			null
		}

		"cryptoHandler" -> cryptoHandler(
			text = a[0].str().orEmpty(),
			iv = a[1].str().orEmpty(),
			key = a[2].str().orEmpty(),
			encrypt = a[3].bool() ?: false,
		)

		else -> Unit
	}

	/** AES/CBC/PKCS7 with UTF-8 key and IV; on any failure the input comes back, like upstream. */
	private fun cryptoHandler(text: String, iv: String, key: String, encrypt: Boolean): String = try {
		val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
		val keySpec = SecretKeySpec(key.toByteArray(), "AES")
		val ivSpec = IvParameterSpec(iv.toByteArray())
		if (encrypt) {
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
			Base64.getEncoder().encodeToString(cipher.doFinal(text.toByteArray()))
		} else {
			cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
			String(cipher.doFinal(Base64.getDecoder().decode(text)))
		}
	} catch (e: Exception) {
		text
	}

	companion object {

		/** Calls that exist upstream but are not implemented here yet; they fail loudly when used. */
		val UNSUPPORTED = setOf(
			"encryptAESCryptoJS", "decryptAESCryptoJS", "decryptAESGCM", "deobfuscateJsPassword",
			"unpackJsAndCombine", "unpackJs", "parseDates", "evaluateJavascriptViaWebview",
		)
	}
}
