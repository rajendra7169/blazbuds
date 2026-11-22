package com.blazify.buds.ui

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import kotlin.coroutines.resume

@OptIn(kotlinx.cinterop.BetaInteropApi::class)
actual suspend fun fetchText(url: String): String? {
    val address = NSURL.URLWithString(url) ?: return null
    val request = NSMutableURLRequest.requestWithURL(address).apply {
        setTimeoutInterval(10.0)
        setValue("application/vnd.github+json", forHTTPHeaderField = "Accept")
        setValue("Blazbuds", forHTTPHeaderField = "User-Agent")
    }
    return suspendCancellableCoroutine { done ->
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            val ok = error == null && (response as? NSHTTPURLResponse)?.statusCode?.toInt() == 200
            val text = if (ok && data != null) NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() else null
            done.resume(text)
        }
        done.invokeOnCancellation { task.cancel() }
        task.resume()
    }
}
