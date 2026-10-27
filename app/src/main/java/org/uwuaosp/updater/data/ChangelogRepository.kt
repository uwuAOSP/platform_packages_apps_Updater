/*
 * SPDX-FileCopyrightText: 2026 The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.uwuaosp.updater.data

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ChangelogBlock(val text: AnnotatedString, val heading: Int = 0, val code: Boolean = false)
data class ChangelogDocument(val url: String, val blocks: List<ChangelogBlock>)

class ChangelogRepository {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).build()
    private var cached: Pair<String, ChangelogDocument>? = null
    private var cachedAt = 0L

    suspend fun load(release: String): ChangelogDocument {
        require(release.matches(Regex("\\d+\\.\\d+\\.\\d{3,}"))) { "Invalid uwu release" }
        synchronized(this) {
            cached?.takeIf { it.first == release && android.os.SystemClock.elapsedRealtime() - cachedAt < 300_000 }
                ?.let { return it.second }
        }
        val url = "https://uwuaosp.uwuniverse.org/blog/uwu-$release/"
        val html = fetch(url)
        val result = withContext(Dispatchers.IO) {
            val document = Jsoup.parse(html, url)
            val titleVersion = Regex("uwuAOSP\\s+(\\d+\\.\\d+\\.\\d+)").find(document.title())?.groupValues?.get(1)
            require(titleVersion == release) { "Changelog version mismatch" }
            val article = document.selectFirst("main .vp-doc")
                ?: document.selectFirst(".uwu-release-page")
                ?: throw IOException("Missing changelog article")
            article.select("script, style, iframe, object, embed, svg, form, button, nav, header, img, video, audio, template, figure, .header-anchor").remove()
            val blocks = mutableListOf<ChangelogBlock>()
            fun walk(element: Element, depth: Int = 0) {
                val tag = element.tagName()
                when {
                    tag.matches(Regex("h[2-6]")) -> blocks += ChangelogBlock(inline(element, url), tag.drop(1).toInt())
                    tag == "p" || tag == "blockquote" -> blocks += ChangelogBlock(inline(element, url))
                    tag == "pre" -> blocks += ChangelogBlock(AnnotatedString(element.wholeText()), code = true)
                    tag == "li" -> {
                        val copy = element.clone()
                        copy.select("ul, ol").remove()
                        blocks += ChangelogBlock(buildAnnotatedString {
                            append("  ".repeat(depth.coerceAtMost(8))); append("• "); append(inline(copy, url))
                        })
                        element.children().filter { it.tagName() == "ul" || it.tagName() == "ol" }
                            .forEach { walk(it, depth + 1) }
                    }
                    tag == "tr" -> blocks += ChangelogBlock(AnnotatedString(element.children().joinToString(" | ") { it.text() }))
                    else -> element.children().forEach { walk(it, depth) }
                }
            }
            walk(article)
            require(blocks.any { it.text.isNotBlank() }) { "Empty changelog" }
            ChangelogDocument(url, blocks.filter { it.text.isNotBlank() })
        }
        synchronized(this) { cached = release to result; cachedAt = android.os.SystemClock.elapsedRealtime() }
        return result
    }

    private suspend fun fetch(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val html = response.use {
                        if (!it.isSuccessful) throw IOException("Changelog HTTP ${it.code}")
                        val body = it.body ?: throw IOException("Empty changelog")
                        val source = body.source()
                        if (source.request(2_000_001)) throw IOException("Changelog exceeds size limit")
                        source.buffer.readUtf8()
                    }
                    continuation.resume(html)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun inline(root: Element, base: String): AnnotatedString = buildAnnotatedString {
        fun visit(node: Node) {
            if (node is TextNode) { append(node.text()); return }
            if (node !is Element || node.tagName() in setOf("ul", "ol")) return
            if (node.tagName() == "br") { append("\n"); return }
            val style = when (node.tagName()) {
                "strong", "b" -> SpanStyle(fontWeight = FontWeight.Bold)
                "em", "i" -> SpanStyle(fontStyle = FontStyle.Italic)
                "code" -> SpanStyle(fontFamily = FontFamily.Monospace)
                else -> null
            }
            val styleIndex = style?.let { pushStyle(it) }
            val href = if (node.tagName() == "a") runCatching {
                URI(base).resolve(node.attr("href")).takeIf { it.scheme in setOf("https", "http") }?.toString()
            }.getOrNull() else null
            val linkIndex = href?.let { pushLink(LinkAnnotation.Url(it)) }
            node.childNodes().forEach { visit(it) }
            linkIndex?.let { pop(it) }
            styleIndex?.let { pop(it) }
        }
        root.childNodes().forEach { visit(it) }
    }
}
