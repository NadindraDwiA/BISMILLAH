package com.example.bismillah.engine.ocr

/**
 * CTC greedy decoding untuk output Text Recognizer PaddleOCR.
 *
 * @param outputTensor logits bentuk [1, T, C] (batch=1, T time-step, C kelas).
 * @param dictKeys kamus karakter; indeks model 1..N memetakan ke dictKeys[0..N-1].
 * @return teks hasil decode.
 *
 * Aturan: argmax per time-step → abaikan indeks 0 (blank) → abaikan indeks
 * yang sama dengan time-step sebelumnya → petakan sisa ke kamus.
 */
fun decodeCtcOutput(outputTensor: Array<Array<FloatArray>>, dictKeys: List<String>): String {
    val batch = outputTensor.firstOrNull() ?: return ""
    val sb = StringBuilder()
    var prev = -1
    for (logits in batch) {
        var best = 0
        var bestV = Float.NEGATIVE_INFINITY
        for (i in logits.indices) {
            if (logits[i] > bestV) {
                bestV = logits[i]
                best = i
            }
        }
        if (best == 0 || best == prev) {
            prev = best
            continue
        }
        prev = best
        val keyIndex = best - 1
        if (keyIndex in dictKeys.indices) {
            sb.append(dictKeys[keyIndex])
        }
    }
    return sb.toString()
}
