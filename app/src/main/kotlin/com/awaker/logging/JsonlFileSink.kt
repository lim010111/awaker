package com.awaker.logging

import java.io.BufferedWriter
import java.io.File

/** append-only JSONL 파일 (ADR-0012 — 샘플당 DB insert 금지). */
class JsonlFileSink(file: File) : SessionLogSink {

    private val writer: BufferedWriter
    private var linesSinceFlush = 0
    private var closed = false

    init {
        file.parentFile?.mkdirs()
        writer = BufferedWriter(file.writer(), BUFFER_BYTES)
    }

    @Synchronized
    override fun writeLine(line: String) {
        if (closed) return
        writer.write(line)
        writer.write("\n")
        // 프로세스 급사 시 손실을 제한하기 위한 주기적 flush.
        if (++linesSinceFlush >= FLUSH_EVERY_LINES) {
            writer.flush()
            linesSinceFlush = 0
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching {
            writer.flush()
            writer.close()
        }
    }

    private companion object {
        const val BUFFER_BYTES = 1 shl 16
        const val FLUSH_EVERY_LINES = 200
    }
}
